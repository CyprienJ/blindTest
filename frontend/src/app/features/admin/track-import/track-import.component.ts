import { CommonModule } from '@angular/common';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormControl,
  FormGroup,
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  CoverPickerComponent,
  CoverSelection,
  coverUpdate,
  KEEP_COVER,
} from '../../../shared/cover-picker/cover-picker.component';
import { DifficultyRatingComponent } from '../../../shared/difficulty-rating/difficulty-rating.component';
import {
  ExcerptEditorComponent,
  formatExcerptTime,
} from '../../../shared/excerpt-editor/excerpt-editor.component';
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
import {
  AdminTrack,
  MediaPreview,
  TrackAnswerType,
  TrackUpdate,
  TrackWaveform,
} from '../track-list/admin-track.model';
import { AdminTrackService } from '../track-list/admin-track.service';
import {
  STREAMING_LINK_PLATFORMS,
  streamingLinkControls,
  streamingLinkFormValue,
  streamingLinksUpdate,
} from '../track-list/streaming-link-fields';
import { AdminImportService } from './admin-import.service';
import { ImportRealtimeService } from './import-realtime.service';
import {
  ImportAliasDraft,
  ImportJob,
  ImportJobStatus,
  ImportSource,
  ImportTrackRequest,
  RealtimeConnectionState,
} from './track-import.model';

const ACTIVE_JOB_KEY = 'active_admin_import_job';
const IMPORT_ALIASES_KEY = 'active_admin_import_aliases';
/** Mirrors the server's default app.import.max-size-bytes so oversized files fail before upload. */
const MAX_UPLOAD_BYTES = 25 * 1024 * 1024;
type AliasFormGroup = FormGroup<{
  type: FormControl<TrackAnswerType>;
  value: FormControl<string>;
}>;

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-track-import',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    DifficultyRatingComponent,
    ExcerptEditorComponent,
    CoverPickerComponent,
  ],
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
  readonly streamingPlatforms = STREAMING_LINK_PLATFORMS;
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
    excerptStartSeconds: [0, [Validators.required, Validators.min(0)]],
    excerptDurationSeconds: [60, [Validators.required, Validators.min(5), Validators.max(60)]],
    imageUrl: ['', Validators.maxLength(500)],
    difficulty: this.formBuilder.control<number | null>(null, Validators.required),
    streamingLinks: this.formBuilder.nonNullable.group(streamingLinkControls()),
    aliases: this.aliasControls,
  });

  readonly uploadForm = this.formBuilder.nonNullable.group({
    title: ['', Validators.maxLength(255)],
    artist: ['', Validators.maxLength(255)],
  });
  readonly maxUploadLabel = '25 MB';
  source: ImportSource = 'UPLOAD';
  selectedFile: File | null = null;
  fileError = '';
  uploadProgress: number | null = null;
  dragActive = false;

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
  waveform: TrackWaveform | null = null;
  waveformLoading = false;
  waveformError = '';
  workflowBusy = false;
  workflowError = '';
  confirmReject = false;
  selectionSaving = false;
  selectionValidated = false;
  selectionError = '';
  coverSelection: CoverSelection = KEEP_COVER;
  private reviewTrackId: string | null = null;

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
      this.uploadForm.disable();
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

  get importLocked(): boolean {
    return this.submitting || this.recovering || !!this.job;
  }

  selectSource(source: ImportSource): void {
    if (this.importLocked) {
      return;
    }
    this.source = source;
    this.submitError = '';
  }

  onFileSelected(input: HTMLInputElement): void {
    this.setFile(input.files?.[0] ?? null);
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = !this.importLocked;
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
    if (!this.importLocked) {
      this.setFile(event.dataTransfer?.files?.[0] ?? null);
    }
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024 * 1024) {
      return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  startUpload(): void {
    const file = this.selectedFile;
    if (this.importLocked) {
      return;
    }
    if (!file) {
      this.fileError = 'Choose an audio file to import.';
      return;
    }
    if (this.uploadForm.invalid) {
      this.uploadForm.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.submitError = '';
    this.uploadProgress = 0;
    this.activity = [];
    this.appendActivity(`Uploading ${file.name}.`);
    const metadata = this.uploadForm.getRawValue();
    this.uploadForm.disable();
    this.importService
      .upload(file, metadata)
      .pipe(
        finalize(() => {
          this.submitting = false;
          this.uploadProgress = null;
        }),
      )
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress) {
            this.uploadProgress = event.total ? Math.round((event.loaded / event.total) * 100) : 0;
          } else if (event.type === HttpEventType.Response && event.body) {
            this.acceptJob(event.body.jobId);
          }
        },
        error: (error) => {
          this.uploadForm.enable();
          this.submitError = this.errorMessage(error);
        },
      });
  }

  startImport(): void {
    const youtubeUrl = this.importForm.controls.youtubeUrl;
    if (this.submitting || this.job || youtubeUrl.invalid) {
      youtubeUrl.markAsTouched();
      return;
    }
    const value = this.importForm.getRawValue();
    const request: ImportTrackRequest = {
      youtubeUrl: value.youtubeUrl.trim(),
    };

    this.submitting = true;
    this.submitError = '';
    this.activity = [];
    this.appendActivity('Submitting the YouTube URL and metadata.');
    this.importService
      .start(request)
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: ({ jobId }) => this.acceptJob(jobId),
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

  loadWaveform(): void {
    const track = this.job?.track;
    if (!track || this.waveformLoading || this.waveform) {
      return;
    }
    this.waveformLoading = true;
    this.waveformError = '';
    this.trackService
      .waveform(track.id)
      .pipe(finalize(() => (this.waveformLoading = false)))
      .subscribe({
        next: (waveform) => {
          this.waveform = waveform;
        },
        error: (error) => (this.waveformError = this.errorMessage(error)),
      });
  }

  retryAliases(): void {
    if (this.job?.track) {
      this.saveAliases(this.job.track.id, () => {
        this.selectionValidated = true;
        this.loadPreview();
      });
    }
  }

  validateSelection(): void {
    const track = this.job?.track;
    if (!track || this.selectionSaving || this.reviewInvalid()) {
      this.importForm.markAllAsTouched();
      return;
    }
    this.selectionSaving = true;
    this.selectionError = '';
    this.previewError = '';
    this.aliasDrafts = this.importForm
      .getRawValue()
      .aliases.map((alias) => ({ type: alias.type, value: alias.value.trim() }))
      .filter((alias) => alias.value.length > 0);
    sessionStorage.setItem(IMPORT_ALIASES_KEY, JSON.stringify(this.aliasDrafts));
    this.trackService.update(track.id, this.trackUpdate()).subscribe({
      next: (updated) => {
        if (this.job) {
          this.job = { ...this.job, track: updated };
        }
        this.coverSelection = KEEP_COVER;
        this.saveAliases(
          updated.id,
          () => {
            this.selectionSaving = false;
            this.selectionValidated = true;
            this.appendActivity(
              `Excerpt: ${this.formatTime(updated.excerptStartSeconds)} for ` +
                `${updated.excerptDurationSeconds}s.`,
            );
            this.loadPreview();
          },
          () => (this.selectionSaving = false),
        );
      },
      error: (error) => {
        this.selectionSaving = false;
        this.selectionError = this.errorMessage(error);
      },
    });
  }

  invalidateSelection(): void {
    this.selectionValidated = false;
    this.selectionError = '';
  }

  get maxExcerptStart(): number {
    return Math.max(
      0,
      (this.job?.track?.durationSeconds ?? this.excerptWindow) - this.excerptWindow,
    );
  }

  get excerptWindow(): number {
    return Number(this.importForm.controls.excerptDurationSeconds.value) || 60;
  }

  readonly formatTime = formatExcerptTime;

  showBlockedPlayback(): void {
    this.previewError = 'Your browser blocked automatic playback. Press play to listen.';
  }

  get excerptStart(): number {
    return Number(this.importForm.controls.excerptStartSeconds.value) || 0;
  }

  publish(): void {
    const track = this.job?.track;
    if (!track || this.workflowBusy || !this.selectionValidated || !this.preview) {
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
    this.waveform = null;
    this.waveformLoading = false;
    this.waveformError = '';
    this.aliasState = 'idle';
    this.aliasError = '';
    this.aliasDrafts = [];
    this.selectionSaving = false;
    this.selectionValidated = false;
    this.selectionError = '';
    this.coverSelection = KEEP_COVER;
    this.reviewTrackId = null;
    this.activity = [];
    this.selectedFile = null;
    this.fileError = '';
    this.uploadProgress = null;
    this.dragActive = false;
    this.uploadForm.enable();
    this.uploadForm.reset({ title: '', artist: '' });
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
      excerptStartSeconds: 0,
      excerptDurationSeconds: 60,
      imageUrl: '',
      difficulty: null,
    });
    this.importForm.controls.streamingLinks.reset();
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

  private setFile(file: File | null): void {
    this.fileError = '';
    this.submitError = '';
    if (!file) {
      return;
    }
    this.selectedFile = null;
    if (file.size === 0) {
      this.fileError = 'The selected file is empty.';
    } else if (file.size > MAX_UPLOAD_BYTES) {
      this.fileError = `The file is larger than ${this.maxUploadLabel}.`;
    } else {
      this.selectedFile = file;
    }
  }

  private acceptJob(jobId: string): void {
    sessionStorage.setItem(ACTIVE_JOB_KEY, jobId);
    this.importForm.disable();
    this.uploadForm.disable();
    this.appendActivity(`Import accepted · ${this.shortJobId(jobId)}.`);
    this.startMonitoring(jobId);
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
    this.source = job.source ?? this.source;
    this.recovering = false;
    this.monitorError = '';
    if (changed) {
      this.appendActivity(`${this.statusLabel(job.status)} · ${job.progress}%.`);
    }
    if (job.status === 'DONE') {
      this.pollingSubscription?.unsubscribe();
      this.pollingSubscription = null;
      this.realtime.disconnect();
      this.prepareReview(job.track);
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

  private saveAliases(
    trackId?: string,
    onSaved: () => void = () => {},
    onError: () => void = () => {},
  ): void {
    if (!trackId) {
      this.aliasState = 'error';
      this.aliasError = 'The finalized track is missing. Refresh the import status.';
      onError();
      return;
    }
    if (this.aliasDrafts.length === 0) {
      this.aliasState = 'saved';
      sessionStorage.removeItem(IMPORT_ALIASES_KEY);
      onSaved();
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
              onSaved();
            },
            error: (error) => {
              this.aliasState = 'error';
              this.aliasError = this.errorMessage(error);
              onError();
            },
          });
      },
      error: (error) => {
        this.aliasState = 'error';
        this.aliasError = this.errorMessage(error);
        onError();
      },
    });
  }

  private prepareReview(track: AdminTrack | null): void {
    if (!track || this.reviewTrackId === track.id) {
      return;
    }
    this.reviewTrackId = track.id;
    this.importForm.patchValue({
      title: track.title,
      artist: track.artist,
      album: track.album ?? '',
      composer: track.composer ?? '',
      work: track.work ?? '',
      category: track.category ?? '',
      decade: track.decade?.toString() ?? '',
      durationSeconds: track.durationSeconds?.toString() ?? '',
      excerptStartSeconds: track.excerptStartSeconds,
      excerptDurationSeconds: track.excerptDurationSeconds ?? 60,
      imageUrl: track.imageUrl ?? '',
      difficulty: track.difficulty,
      streamingLinks: streamingLinkFormValue(track),
    });
    this.importForm.enable();
    this.importForm.controls.youtubeUrl.disable();
    this.aliasState = 'idle';
    this.loadPreview();
    this.loadWaveform();
  }

  private reviewInvalid(): boolean {
    return this.importForm.invalid || this.excerptStart > this.maxExcerptStart;
  }

  private trackUpdate(): TrackUpdate {
    const value = this.importForm.getRawValue();
    return {
      title: value.title.trim(),
      artist: value.artist.trim(),
      album: this.optional(value.album),
      composer: this.optional(value.composer),
      work: this.optional(value.work),
      category: this.optional(value.category),
      decade: this.optionalNumber(value.decade),
      durationSeconds: this.optionalNumber(value.durationSeconds),
      excerptStartSeconds: this.excerptStart,
      excerptDurationSeconds: this.excerptWindow,
      imageUrl: this.optional(value.imageUrl),
      difficulty: value.difficulty ?? undefined,
      streamingLinks: streamingLinksUpdate(value.streamingLinks),
      ...coverUpdate(this.coverSelection),
    };
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
      return this.source === 'UPLOAD'
        ? 'Check the title and artist fields.'
        : 'Check the YouTube URL and metadata fields.';
    }
    if (code === 'IMPORT_FILE_TOO_LARGE' || error.status === 413) {
      return `The file is larger than ${this.maxUploadLabel}.`;
    }
    if (code === 'IMPORT_FILE_UNSUPPORTED') {
      return 'This file is not a supported audio file (MP3, M4A/AAC, FLAC, Ogg/Opus, WAV or WebM).';
    }
    if (code === 'IMPORT_AUDIO_TOO_LONG') {
      return 'The audio is longer than 15 minutes.';
    }
    if (code === 'IMPORT_METADATA_REQUIRED') {
      return 'This file has no title or artist tags. Fill in both fields.';
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
