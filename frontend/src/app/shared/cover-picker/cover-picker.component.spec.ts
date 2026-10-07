import { HttpErrorResponse } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { CoverSuggestion } from '../../features/admin/track-list/admin-track.model';
import { AdminTrackService } from '../../features/admin/track-list/admin-track.service';
import { CoverPickerComponent, coverUpdate, KEEP_COVER } from './cover-picker.component';

describe('CoverPickerComponent', () => {
  const suggestions: CoverSuggestion[] = [
    { candidateId: 'c1', source: 'DEEZER', album: 'Hunting High and Low', artist: 'a-ha' },
    { candidateId: 'c2', source: 'ITUNES', album: 'Headlines', artist: 'a-ha' },
  ];
  let service: jasmine.SpyObj<AdminTrackService>;

  function setup(currentCoverUrl: string | null = null, suggest = true) {
    service = jasmine.createSpyObj<AdminTrackService>('AdminTrackService', [
      'suggestCovers',
      'coverThumbnail',
      'uploadCover',
    ]);
    service.suggestCovers.and.returnValue(of(suggestions));
    service.coverThumbnail.and.returnValue(of(new Blob(['x'], { type: 'image/jpeg' })));
    TestBed.configureTestingModule({
      providers: [{ provide: AdminTrackService, useValue: service }],
    });
    const fixture = TestBed.createComponent(CoverPickerComponent);
    fixture.componentRef.setInput('title', 'Take On Me');
    fixture.componentRef.setInput('artist', 'a-ha');
    fixture.componentRef.setInput('currentCoverUrl', currentCoverUrl);
    fixture.componentRef.setInput('suggest', suggest);
    fixture.detectChanges();
    return { fixture, picker: fixture.componentInstance };
  }

  function options(fixture: { nativeElement: HTMLElement }): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.cover-option'));
  }

  it('searches once the metadata settles and shows thumbnails through object URLs', fakeAsync(() => {
    const { fixture, picker } = setup();
    fixture.componentRef.setInput('album', 'Hunting High and Low ');
    fixture.detectChanges();
    tick(700);
    fixture.detectChanges();

    expect(service.suggestCovers).toHaveBeenCalledOnceWith({
      title: 'Take On Me',
      artist: 'a-ha',
      album: 'Hunting High and Low',
    });
    expect(service.coverThumbnail).toHaveBeenCalledTimes(2);
    expect(picker.tiles().every((tile) => tile.thumbnailUrl?.startsWith('blob:'))).toBeTrue();
    expect(
      options(fixture).map((option) => option.querySelector('.caption')?.textContent?.trim()),
    ).toEqual(['Hunting High and Low', 'Headlines', 'No cover']);
    expect(fixture.nativeElement.querySelector('.cover-option small').textContent).toBe('Deezer');
  }));

  it('does not search without a title and artist', fakeAsync(() => {
    const { fixture } = setup();
    fixture.componentRef.setInput('artist', '  ');
    fixture.detectChanges();
    tick(700);

    expect(service.suggestCovers).not.toHaveBeenCalled();
  }));

  it('only offers uploads when suggestions are off', fakeAsync(() => {
    const { fixture } = setup(null, false);
    tick(700);
    fixture.detectChanges();

    expect(service.suggestCovers).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.hint').textContent).toContain(
      'JPEG, PNG or WebP, up to 5 MB.',
    );
  }));

  it('selects a suggestion, keeps it across a new search, and can keep or remove the current cover', fakeAsync(() => {
    const { fixture, picker } = setup('/api/covers/current');
    tick(700);
    fixture.detectChanges();
    expect(picker.selection()).toEqual(KEEP_COVER);

    options(fixture)[2].click();
    expect(picker.selection()).toEqual({ kind: 'candidate', candidateId: 'c2' });

    service.suggestCovers.and.returnValue(
      of([
        { candidateId: 'c3', source: 'COVER_ART_ARCHIVE', album: 'Scoundrel Days', artist: 'a-ha' },
      ]),
    );
    fixture.componentRef.setInput('title', 'The Sun Always Shines on T.V.');
    fixture.detectChanges();
    tick(700);
    expect(picker.tiles().map((tile) => tile.candidateId)).toEqual(['c2', 'c3']);

    picker.chooseNoCover();
    expect(picker.selection()).toEqual({ kind: 'remove' });
    picker.keep();
    expect(picker.selection()).toEqual(KEEP_COVER);
  }));

  it('reports an unavailable search without blocking uploads', fakeAsync(() => {
    const { fixture, picker } = setup();
    service.suggestCovers.and.returnValue(throwError(() => new Error('offline')));
    fixture.componentRef.setInput('title', 'Hunting High and Low');
    fixture.detectChanges();
    tick(700);
    fixture.detectChanges();

    expect(picker.searchState()).toBe('error');
    expect(fixture.nativeElement.textContent).toContain('Cover search is unavailable');
  }));

  it('uploads an image, selects it, and explains rejected files', () => {
    const { picker } = setup();
    const uploaded = new Subject<CoverSuggestion>();
    service.uploadCover.and.returnValue(uploaded);
    const file = new File(['png'], 'cover.png', { type: 'image/png' });

    picker.upload({ files: [file], value: 'cover.png' } as unknown as HTMLInputElement);
    expect(picker.uploading()).toBeTrue();
    uploaded.next({ candidateId: 'u1', source: 'UPLOAD', album: '', artist: '' });

    expect(picker.selection()).toEqual({ kind: 'candidate', candidateId: 'u1' });
    expect(picker.tiles()[0].thumbnailUrl).toMatch(/^blob:/);

    service.uploadCover.and.returnValue(
      throwError(
        () => new HttpErrorResponse({ status: 415, error: { code: 'COVER_IMAGE_UNSUPPORTED' } }),
      ),
    );
    picker.upload({ files: [file], value: 'cover.png' } as unknown as HTMLInputElement);
    expect(picker.uploadError()).toBe('Choose a JPEG, PNG or WebP image.');

    const large = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.jpg');
    service.uploadCover.calls.reset();
    picker.upload({ files: [large], value: 'big.jpg' } as unknown as HTMLInputElement);
    expect(service.uploadCover).not.toHaveBeenCalled();
    expect(picker.uploadError()).toBe('The image is larger than 5 MB.');
  });

  it('maps a selection to the track update fields', () => {
    expect(coverUpdate(KEEP_COVER)).toEqual({});
    expect(coverUpdate({ kind: 'candidate', candidateId: 'c1' })).toEqual({
      coverCandidateId: 'c1',
    });
    expect(coverUpdate({ kind: 'remove' })).toEqual({ removeCover: true });
  });
});
