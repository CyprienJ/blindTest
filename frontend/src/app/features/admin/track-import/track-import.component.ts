import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnDestroy, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormControl,
  FormGroup,
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import {
  catchError,
  concatMap,
  EMPTY,
  finalize,
  from,
  Subscription,
  switchMap,
  tap,
  timer,
} from 'rxjs';
import { MediaPreview, TrackAnswerType } from '../track-list/admin-track.model';
import { AdminTrackService } from '../track-list/admin-track.service';
import { AdminImportService } from './admin-import.service';
import { ImportRealtimeService } from './import-realtime.service';
import {
  ImportAliasDraft,
  ImportJob,
  ImportJobStatus,
  ImportTrackRequest,
  RealtimeConnectionState,
} from './track-import.model';

const ACTIVE_JOB_KEY = 'active_admin_import_job';
const IMPORT_ALIASES_KEY = 'active_admin_import_aliases';
type AliasFormGroup = FormGroup<{
  type: FormControl<TrackAnswerType>;
  value: FormControl<string>;
}>;

@Component({
  selector: 'app-track-import',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, RouterLinkActive],
  templateUrl: './track-import.component.html',
  styleUrl: './track-import.component.scss',
})
export class TrackImportComponent implements OnInit, OnDestroy {
  private readonly formBuilder = inject(FormBuilder);
  private readonly importService = inject(AdminImportService);
  private readonly trackService = inject(AdminTrackService);
  private readonly realtime = inject(ImportRealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly aliasTypes: TrackAnswerType[] = ['TITLE', 'ARTIST', 'COMPOSER', 'ALBUM'];
  private readonly aliasControls = this.formBuilder.array<AliasFormGroup>([]);
  readonly importForm = this.formBuilder.nonNullable.group({
    youtubeUrl: [
      '',
      [
        Validators.required,
        Validators.maxLength(500),
        Validators.pattern(/^https?:\/\/(?:www\.)?(?:youtube\.com|youtu\.be)\//i),
      ],
    ],
    title: ['', [Validators.required, Validators.maxLength(255)]],
    artist: ['', [Validators.required, Validators.maxLength(255)]],
    album: ['', Validators.maxLength(255)],
    composer: ['', Validators.maxLength(255)],
    work: ['', Validators.maxLength(255)],
    category: ['', Validators.maxLength(100)],
    decade: ['', [Validators.pattern(/^\d*$/), Validators.min(1900), Validators.max(2090)]],
    durationSeconds: ['', [Validators.pattern(/^\d*$/), Validators.min(1)]],
    imageUrl: ['', Validators.maxLength(500)],
    aliases: this.aliasControls,
  });

  submitting = false;
  recovering = false;
  submitError = '';
  monitorError = '';
  job: ImportJob | null = null;
  realtimeState: RealtimeConnectionState = 'offline';
  activity: string[] = [];

  aliasState: 'idle' | 'saving' | 'saved' | 'error' = 'idle';
  aliasError = '';
  private aliasDrafts: ImportAliasDraft[] = [];

  preview: MediaPreview | null = null;
  previewLoading = false;
  previewError = '';
  workflowBusy = false;
  workflowError = '';
  confirmReject = false;

  private pollingSubscription: Subscription | null = null;
  private realtimeSubscription: Subscription | null = null;

  get aliases(): FormArray<AliasFormGroup> {
    return this.importForm.controls.aliases;
  }

  ngOnInit(): void {
    this.realtime.connectionState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => (this.realtimeState = state));
    const savedJobId = sessionStorage.getItem(ACTIVE_JOB_KEY);
    if (savedJobId) {
      this.aliasDrafts = this.readSavedAliases();
      this.importForm.disable();
      this.recovering = true;
      this.appendActivity('Recovering the active import from the server.');
      this.startMonitoring(savedJobId);
    }
  }

  ngOnDestroy(): void {
    this.stopMonitoring();
  }

  addAlias(): void {
    if (this.importForm.disabled || this.aliases.length >= 10) {
      return;
    }
    this.aliases.push(
      this.formBuilder.nonNullable.group({
        type: ['TITLE' as TrackAnswerType, Validators.required],
        value: ['', [Validators.required, Validators.maxLength(255)]],
      }),
    );
  }

  removeAlias(index: number): void {
    if (this.importForm.enabled) {
      this.aliases.removeAt(index);
    }
  }

  startImport(): void {
    if (this.submitting || this.job || this.importForm.invalid) {
      this.importForm.markAllAsTouched();
      return;
    }
    const value = this.importForm.getRawValue();
    this.aliasDrafts = value.aliases
      .map((alias) => ({ type: alias.type, value: alias.value.trim() }))
      .filter((alias) => alias.value.length > 0);
    const request: ImportTrackRequest = {
      youtubeUrl: value.youtubeUrl.trim(),
      title: value.title.trim(),
      artist: value.artist.trim(),
      album: this.optional(value.album),
      composer: this.optional(value.composer),
      work: this.optional(value.work),
      category: this.optional(value.category),
      decade: this.optionalNumber(value.decade),
      durationSeconds: this.optionalNumber(value.durationSeconds),
      imageUrl: this.optional(value.imageUrl),
    };

    this.submitting = true;
    this.submitError = '';
    this.activity = [];
    this.appendActivity('Submitting the YouTube URL and metadata.');
    this.importService
      .start(request)
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: ({ jobId }) => {
          sessionStorage.setItem(ACTIVE_JOB_KEY, jobId);
          sessionStorage.setItem(IMPORT_ALIASES_KEY, JSON.stringify(this.aliasDrafts));
          this.importForm.disable();
          this.appendActivity(`Import accepted · ${this.shortJobId(jobId)}.`);
          this.startMonitoring(jobId);
        },
        error: (error) => (this.submitError = this.errorMessage(error)),
      });
  }

  retryPolling(): void {
    if (this.job?.jobId) {
      this.monitorError = '';
      this.pollJob(this.job.jobId);
    }
  }

  loadPreview(): void {
    const track = this.job?.track;
    if (!track || this.previewLoading) {
      return;
    }
    this.previewLoading = true;
    this.previewError = '';
    this.trackService
      .preview(track.id)
      .pipe(finalize(() => (this.previewLoading = false)))
      .subscribe({
        next: (preview) => {
          this.preview = preview;
          this.appendActivity('Secure audio preview ready.');
        },
        error: (error) => (this.previewError = this.errorMessage(error)),
      });
  }

  retryAliases(): void {
    if (this.job?.track) {
      this.saveAliases(this.job.track.id);
    }
  }

  publish(): void {
    const track = this.job?.track;
    if (!track || this.workflowBusy || this.aliasState !== 'saved') {
      return;
    }
    this.workflowBusy = true;
    this.workflowError = '';
    this.trackService
      .validate(track.id)
      .pipe(finalize(() => (this.workflowBusy = false)))
      .subscribe({
        next: (published) => {
          if (this.job) {
            this.job = { ...this.job, status: 'PUBLISHED', track: published };
          }
          this.preview = null;
          this.clearRecoveryState();
          this.appendActivity('Track published to the public catalogue.');
        },
        error: (error) => (this.workflowError = this.errorMessage(error)),
      });
  }

  reject(): void {
    const track = this.job?.track;
    if (!track || this.workflowBusy) {
      return;
    }
    this.workflowBusy = true;
    this.workflowError = '';
    this.trackService
      .reject(track.id)
      .pipe(finalize(() => (this.workflowBusy = false)))
      .subscribe({
        next: (rejected) => {
          if (this.job) {
            this.job = { ...this.job, status: 'REJECTED', track: rejected };
          }
          this.confirmReject = false;
          this.preview = null;
          this.clearRecoveryState();
          this.appendActivity('Track rejected and kept out of the public catalogue.');
        },
        error: (error) => {
          this.confirmReject = false;
          this.workflowError = this.errorMessage(error);
        },
      });
  }

  resetImport(): void {
    this.stopMonitoring();
    this.clearRecoveryState();
    this.job = null;
    this.recovering = false;
    this.submitError = '';
    this.monitorError = '';
    this.workflowError = '';
    this.preview = null;
    this.previewError = '';
    this.aliasState = 'idle';
    this.aliasError = '';
    this.aliasDrafts = [];
    this.activity = [];
    this.importForm.enable();
    this.importForm.reset({
      youtubeUrl: '',
      title: '',
      artist: '',
      album: '',
      composer: '',
      work: '',
      category: '',
      decade: '',
      durationSeconds: '',
      imageUrl: '',
    });
    this.aliases.clear();
  }

  stageState(stage: 'QUEUED' | 'DOWNLOADING' | 'PROCESSING' | 'DONE'): string {
    if (!this.job) {
      return 'pending';
    }
    if (this.job.status === 'FAILED') {
      return 'failed';
    }
    const order = ['QUEUED', 'DOWNLOADING', 'PROCESSING', 'DONE'];
    const effective =
      this.job.status === 'PUBLISHED' || this.job.status === 'REJECTED' ? 'DONE' : this.job.status;
    const currentIndex = order.indexOf(effective);
    const stageIndex = order.indexOf(stage);
    return stageIndex < currentIndex ? 'done' : stageIndex === currentIndex ? 'active' : 'pending';
  }

  statusLabel(status: ImportJobStatus): string {
    const labels: Record<ImportJobStatus, string> = {
      QUEUED: 'Queued',
      DOWNLOADING: 'Downloading',
      PROCESSING: 'Processing',
      DONE: 'Ready for review',
      PUBLISHED: 'Published',
      REJECTED: 'Rejected',
      FAILED: 'Import failed',
    };
    return labels[status];
  }

  shortJobId(jobId: string): string {
    return `job_${jobId.slice(0, 8)}`;
  }

  private startMonitoring(jobId: string): void {
    this.stopMonitoring();
    this.realtimeSubscription = this.realtime.watch(jobId).subscribe({
      next: (event) => {
        if (event.kind === 'progress') {
          this.applyRealtimeProgress(event.status, event.progress);
        } else if (event.kind === 'done') {
          this.appendActivity('WebSocket reported that finalization completed.');
          this.pollJob(jobId);
        } else {
          this.appendActivity(event.errorMessage);
          this.pollJob(jobId);
        }
      },
      error: () => {
        this.appendActivity('WebSocket interrupted; polling remains active.');
      },
    });
    this.pollingSubscription = timer(0, 3000)
      .pipe(
        switchMap(() =>
          this.importService.getJob(jobId).pipe(
            catchError((error) => {
              this.recovering = false;
              this.monitorError = this.errorMessage(error);
              return EMPTY;
            }),
          ),
        ),
      )
      .subscribe((job) => this.applyJob(job));
  }

  private pollJob(jobId: string): void {
    this.importService.getJob(jobId).subscribe({
      next: (job) => this.applyJob(job),
      error: (error) => {
        this.recovering = false;
        this.monitorError = this.errorMessage(error);
      },
    });
  }

  private applyJob(job: ImportJob): void {
    const changed = !this.job || this.job.status !== job.status;
    this.job = job;
    this.recovering = false;
    this.monitorError = '';
    if (changed) {
      this.appendActivity(`${this.statusLabel(job.status)} · ${job.progress}%.`);
    }
    if (job.status === 'DONE') {
      this.pollingSubscription?.unsubscribe();
      this.pollingSubscription = null;
      this.realtime.disconnect();
      if (this.aliasState === 'idle') {
        this.saveAliases(job.track?.id);
      }
    } else if (job.status === 'FAILED') {
      this.stopMonitoring();
      this.clearRecoveryState();
    } else if (job.status === 'PUBLISHED' || job.status === 'REJECTED') {
      this.stopMonitoring();
      this.clearRecoveryState();
      this.aliasState = 'saved';
    }
  }

  private applyRealtimeProgress(status: ImportJobStatus, progress: number): void {
    if (!this.job) {
      return;
    }
    const changed = this.job.status !== status;
    this.job = { ...this.job, status, progress };
    if (changed || progress % 10 === 0) {
      this.appendActivity(`WebSocket · ${this.statusLabel(status)} ${progress}%.`);
    }
  }

  private saveAliases(trackId?: string): void {
    if (!trackId) {
      this.aliasState = 'error';
      this.aliasError = 'The finalized track is missing. Refresh the import status.';
      return;
    }
    if (this.aliasDrafts.length === 0) {
      this.aliasState = 'saved';
      sessionStorage.removeItem(IMPORT_ALIASES_KEY);
      return;
    }
    this.aliasState = 'saving';
    this.aliasError = '';
    this.trackService.listAliases(trackId).subscribe({
      next: (existing) => {
        const missing = this.aliasDrafts.filter(
          (draft) =>
            !existing.some(
              (alias) =>
                alias.type === draft.type &&
                alias.value.trim().toLocaleLowerCase() === draft.value.trim().toLocaleLowerCase(),
            ),
        );
        from(missing)
          .pipe(
            concatMap((alias) =>
              this.trackService
                .saveAlias(trackId, alias.type, alias.value)
                .pipe(tap(() => this.appendActivity(`Saved ${alias.type.toLowerCase()} alias.`))),
            ),
          )
          .subscribe({
            complete: () => {
              this.aliasState = 'saved';
              sessionStorage.removeItem(IMPORT_ALIASES_KEY);
            },
            error: (error) => {
              this.aliasState = 'error';
              this.aliasError = this.errorMessage(error);
            },
          });
      },
      error: (error) => {
        this.aliasState = 'error';
        this.aliasError = this.errorMessage(error);
      },
    });
  }

  private stopMonitoring(): void {
    this.pollingSubscription?.unsubscribe();
    this.realtimeSubscription?.unsubscribe();
    this.pollingSubscription = null;
    this.realtimeSubscription = null;
    this.realtime.disconnect();
  }

  private clearRecoveryState(): void {
    sessionStorage.removeItem(ACTIVE_JOB_KEY);
    sessionStorage.removeItem(IMPORT_ALIASES_KEY);
  }

  private readSavedAliases(): ImportAliasDraft[] {
    try {
      return JSON.parse(sessionStorage.getItem(IMPORT_ALIASES_KEY) ?? '[]') as ImportAliasDraft[];
    } catch {
      return [];
    }
  }

  private appendActivity(message: string): void {
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    this.activity = [...this.activity.slice(-7), `${timestamp} · ${message}`];
  }

  private optional(value: string): string | null {
    return value.trim() || null;
  }

  private optionalNumber(value: string): number | null {
    return value ? Number(value) : null;
  }

  private errorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'The operation could not be completed. Please try again.';
    }
    const code = error.error?.code as string | undefined;
    const message = error.error?.message as string | undefined;
    if (code === 'IMPORT_ALREADY_ACTIVE') {
      return 'This YouTube video already has an active import.';
    }
    if (code === 'VALIDATION_FAILED') {
      return 'Check the YouTube URL and metadata fields.';
    }
    if (code === 'TRACK_METADATA_INCOMPLETE') {
      return 'Complete the required metadata before publishing.';
    }
    if (code === 'TRACK_EXCERPT_INVALID') {
      return 'The imported audio is too short for the selected excerpt.';
    }
    if (error.status === 0) {
      return 'The server cannot be reached. Polling will retry automatically.';
    }
    return message || 'The operation could not be completed. Please try again.';
  }
}
