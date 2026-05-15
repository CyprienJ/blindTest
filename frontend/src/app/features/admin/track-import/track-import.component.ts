import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AdminService } from '../../../core/services/admin.service';
import { StompService } from '../../../core/services/stomp.service';
import { AdminSidebarComponent } from '../admin-sidebar.component';
import { ImportJob, WsImportDoneEvent, WsImportEvent, WsImportFailedEvent, WsImportProgressEvent } from '../../../core/models/admin.model';

type Stage = 'QUEUED' | 'DOWNLOADING' | 'PROCESSING' | 'DONE' | 'FAILED';

const STAGE_ORDER: Stage[] = ['QUEUED', 'DOWNLOADING', 'PROCESSING', 'DONE'];

@Component({
  selector: 'app-track-import',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AdminSidebarComponent],
  template: `
    <div class="bt-screen" style="height: 100vh; overflow: hidden; flex-direction: column;">
      <div class="bt-shell">
        <app-admin-sidebar active="imports" />
        <div class="bt-main">
          <h1 class="bt-h1">Import from YouTube</h1>
          <p class="bt-sub">Paste a URL, fill in metadata. yt-dlp + FFmpeg run server-side; progress streams via WebSocket.</p>

          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 18px;">
            <!-- Left: form -->
            <div class="bt-card">
              <h2 class="bt-h2" style="margin-bottom: 14px;">New import</h2>
              <form [formGroup]="form" (ngSubmit)="submit()" style="display: flex; flex-direction: column; gap: 14px;">
                <div class="bt-field">
                  <label class="bt-label">YouTube URL</label>
                  <input class="bt-input" type="url" formControlName="youtubeUrl" placeholder="https://www.youtube.com/watch?v=..." />
                  @if (form.get('youtubeUrl')?.invalid && form.get('youtubeUrl')?.touched) {
                    <span class="bt-error">Enter a valid YouTube URL</span>
                  }
                </div>

                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
                  <div class="bt-field">
                    <label class="bt-label">Title</label>
                    <input class="bt-input" formControlName="title" placeholder="Track title" />
                    @if (form.get('title')?.invalid && form.get('title')?.touched) {
                      <span class="bt-error">Required</span>
                    }
                  </div>
                  <div class="bt-field">
                    <label class="bt-label">Artist</label>
                    <input class="bt-input" formControlName="artist" placeholder="Artist name" />
                    @if (form.get('artist')?.invalid && form.get('artist')?.touched) {
                      <span class="bt-error">Required</span>
                    }
                  </div>
                  <div class="bt-field">
                    <label class="bt-label">Composer</label>
                    <input class="bt-input" formControlName="composer" placeholder="Composer" />
                  </div>
                  <div class="bt-field">
                    <label class="bt-label">Work / Album</label>
                    <input class="bt-input" formControlName="work" placeholder="Album or work" />
                  </div>
                  <div class="bt-field">
                    <label class="bt-label">Decade</label>
                    <input class="bt-input" type="number" formControlName="decade" placeholder="1980" min="1900" max="2030" />
                  </div>
                  <div class="bt-field">
                    <label class="bt-label">Category</label>
                    <input class="bt-input" formControlName="category" placeholder="Pop, Rock…" />
                  </div>
                </div>

                @if (submitError) {
                  <div style="background: oklch(0.25 0.04 30); border: 1px solid oklch(0.38 0.10 30); border-radius: var(--r-md); padding: 10px 14px; font-size: 13.5px; color: var(--danger);">
                    {{ submitError }}
                  </div>
                }

                <button type="submit" class="bt-btn bt-btn-primary bt-btn-lg" style="width: 100%;" [disabled]="submitting || job?.status === 'QUEUED' || job?.status === 'DOWNLOADING' || job?.status === 'PROCESSING'">
                  {{ submitting ? 'Submitting…' : '↓ Start import' }}
                </button>
              </form>
            </div>

            <!-- Right: pipeline + preview -->
            <div style="display: flex; flex-direction: column; gap: 14px;">
              @if (job) {
                <div class="bt-card">
                  <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                    <h2 class="bt-h2">Pipeline</h2>
                    <span class="bt-chip" [class.bt-chip-warn]="isRunning" [class.bt-chip-accent]="job.status === 'DONE'" [class.bt-chip-danger]="job.status === 'FAILED'">
                      <span class="bt-chip-dot" *ngIf="isRunning"></span>
                      {{ job.status }}
                    </span>
                  </div>

                  @for (stage of STAGE_ORDER; track stage) {
                    <div style="display: flex; gap: 12px; padding: 8px 0;">
                      <div [style.background]="stageState(stage) === 'done' ? 'var(--accent)' : stageState(stage) === 'active' ? 'oklch(0.32 0.10 80)' : 'var(--bg-3)'"
                           style="width: 22px; height: 22px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 11px; font-weight: 700;">
                        @if (stageState(stage) === 'done') { ✓ }
                        @else if (stageState(stage) === 'active') {
                          <span style="width: 7px; height: 7px; border-radius: 50%; background: var(--warn); animation: bt-pulse 1s ease-in-out infinite; display: block;"></span>
                        } @else { · }
                      </div>
                      <div style="flex: 1;">
                        <div style="display: flex; justify-content: space-between; font-size: 13.5px; font-weight: 600;">
                          <span [style.color]="stageState(stage) === 'pending' ? 'var(--fg-3)' : 'var(--fg-0)'">{{ stage }}</span>
                          @if (stageState(stage) === 'active') {
                            <span class="mono" style="color: var(--warn);">{{ job.progress }}%</span>
                          }
                        </div>
                        @if (stageState(stage) === 'active') {
                          <div class="bt-progress" style="margin-top: 6px;"><div [style.width]="job.progress + '%'" style="background: var(--warn);"></div></div>
                        }
                      </div>
                    </div>
                  }

                  @if (job.status === 'FAILED') {
                    <div class="bt-error" style="margin-top: 8px; padding: 10px; background: oklch(0.25 0.04 30); border-radius: var(--r-md);">
                      {{ job.errorMessage }}
                    </div>
                  }
                </div>

                @if (job.status === 'DONE' && job.track) {
                  <div class="bt-card">
                    <h2 class="bt-h2" style="margin-bottom: 12px;">Preview</h2>
                    <div style="display: flex; gap: 14px; align-items: center;">
                      <div class="bt-placeholder" style="width: 64px; height: 64px;">cover</div>
                      <div>
                        <div style="font-size: 14px; font-weight: 700;">{{ job.track.title }}</div>
                        <div style="font-size: 12px; color: var(--fg-2);">{{ job.track.artist }}{{ job.track.decade ? ' · ' + job.track.decade + 's' : '' }}{{ job.track.category ? ' · ' + job.track.category : '' }}</div>
                      </div>
                    </div>
                    <div style="display: flex; gap: 8px; margin-top: 14px;">
                      <button class="bt-btn bt-btn-primary" style="flex: 1;" (click)="validateImport()">✓ Validate (publish)</button>
                      <button class="bt-btn bt-btn-danger" style="flex: 1;" (click)="rejectImport()">✕ Reject</button>
                    </div>
                  </div>
                }
              } @else {
                <div class="bt-card" style="display: flex; align-items: center; justify-content: center; min-height: 180px; color: var(--fg-3); font-size: 13.5px;">
                  No active import. Fill the form and click "Start import".
                </div>
              }
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class TrackImportComponent implements OnInit, OnDestroy {
  private readonly adminService = inject(AdminService);
  private readonly stompService = inject(StompService);
  private readonly fb = inject(FormBuilder);

  readonly STAGE_ORDER = STAGE_ORDER;

  form!: FormGroup;
  submitting = false;
  submitError = '';
  job: ImportJob | null = null;

  private jobSub: Subscription | null = null;

  get isRunning(): boolean {
    return this.job?.status === 'QUEUED' ||
           this.job?.status === 'DOWNLOADING' ||
           this.job?.status === 'PROCESSING';
  }

  stageState(stage: Stage): 'done' | 'active' | 'pending' {
    if (!this.job) return 'pending';
    const jobIdx = STAGE_ORDER.indexOf(this.job.status as Stage);
    const stageIdx = STAGE_ORDER.indexOf(stage);
    if (jobIdx < 0) return 'pending';
    if (stageIdx < jobIdx) return 'done';
    if (stageIdx === jobIdx) return 'active';
    return 'pending';
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      youtubeUrl: ['', [Validators.required]],
      title: ['', Validators.required],
      artist: ['', Validators.required],
      composer: [''],
      work: [''],
      decade: [null],
      category: [''],
    });
    this.stompService.connect();
  }

  ngOnDestroy(): void {
    this.jobSub?.unsubscribe();
    this.stompService.disconnect();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.submitError = '';
    const val = this.form.value;
    this.adminService.startImport({
      youtubeUrl: val.youtubeUrl,
      title: val.title,
      artist: val.artist,
      composer: val.composer || undefined,
      work: val.work || undefined,
      category: val.category || undefined,
      decade: val.decade ? Number(val.decade) : null,
    }).subscribe({
      next: (res) => {
        this.submitting = false;
        this.job = { jobId: res.jobId, status: 'QUEUED', progress: 0, errorMessage: null, createdAt: new Date().toISOString(), track: null };
        this.subscribeToJob(res.jobId);
      },
      error: (err) => {
        this.submitting = false;
        this.submitError = err.status === 400 ? 'Invalid request. Check all required fields.' : 'Import failed. Please try again.';
      },
    });
  }

  private subscribeToJob(jobId: string): void {
    this.jobSub?.unsubscribe();
    this.jobSub = this.stompService.watch<WsImportEvent>(`/user/queue/import/${jobId}`).subscribe({
      next: (event) => {
        if ('progress' in event && 'status' in event && !('trackId' in event) && !('errorMessage' in event)) {
          const e = event as WsImportProgressEvent;
          this.job = { ...this.job!, status: e.status as ImportJob['status'], progress: e.progress };
        } else if ('trackId' in event) {
          this.job = { ...this.job!, status: 'DONE', progress: 100 };
          this.adminService.getImportJob(jobId).subscribe((j) => { this.job = j; });
        } else if ('errorMessage' in event) {
          const e = event as WsImportFailedEvent;
          this.job = { ...this.job!, status: 'FAILED', errorMessage: e.errorMessage };
        }
      },
    });
  }

  validateImport(): void {
    if (!this.job?.track) return;
    this.adminService.validateTrack(this.job.track.id).subscribe({
      next: () => { this.job = null; this.form.reset(); },
    });
  }

  rejectImport(): void {
    if (!this.job?.track) return;
    this.adminService.rejectTrack(this.job.track.id).subscribe({
      next: () => { this.job = null; this.form.reset(); },
    });
  }
}
