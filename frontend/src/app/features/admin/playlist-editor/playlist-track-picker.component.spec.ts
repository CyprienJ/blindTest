import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminTrack, PageResponse } from '../track-list/admin-track.model';
import { AdminTrackService } from '../track-list/admin-track.service';
import { PlaylistTrackPickerComponent } from './playlist-track-picker.component';

describe('PlaylistTrackPickerComponent', () => {
  const track = (id: string) => ({ id, title: `Song ${id}`, difficulty: 3 }) as AdminTrack;
  const page = (content: AdminTrack[], totalElements: number, number: number) =>
    ({ content, totalElements, number }) as PageResponse<AdminTrack>;
  let service: jasmine.SpyObj<AdminTrackService>;

  function setup() {
    service = jasmine.createSpyObj<AdminTrackService>('AdminTrackService', [
      'list',
      'categories',
      'preview',
    ]);
    service.categories.and.returnValue(of(['Pop']));
    service.list.and.returnValue(of(page([track('a'), track('b')], 3, 0)));
    TestBed.configureTestingModule({
      providers: [{ provide: AdminTrackService, useValue: service }],
    });
    const fixture = TestBed.createComponent(PlaylistTrackPickerComponent);
    fixture.componentRef.setInput('playlistId', 'p1');
    fixture.detectChanges();
    return { fixture, picker: fixture.componentInstance };
  }

  it('excludes the playlist server-side and appends further pages', () => {
    const { picker } = setup();

    expect(service.list).toHaveBeenCalledWith(0, 20, 'PUBLISHED', '', {
      category: null,
      decade: null,
      difficulty: null,
      excludePlaylistId: 'p1',
    });
    expect(picker.hasMore).toBeTrue();

    service.list.and.returnValue(of(page([track('b'), track('c')], 3, 1)));
    picker.loadMore();

    expect(picker.tracks.map((item) => item.id)).toEqual(['a', 'b', 'c']);
    expect(picker.hasMore).toBeFalse();
  });

  it('hides tracks added since the last load and counts what is left', () => {
    const { fixture, picker } = setup();

    fixture.componentRef.setInput('memberIds', ['a']);
    fixture.detectChanges();

    expect(picker.visibleTracks.map((item) => item.id)).toEqual(['b']);
    expect(picker.availableCount).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('2 published tracks not in this playlist');
  });

  it('reloads the first page when a filter changes', () => {
    const { picker } = setup();
    service.list.calls.reset();

    picker.filters.controls.difficulty.setValue('4');

    expect(service.list.calls.mostRecent().args[0]).toBe(0);
    expect(service.list.calls.mostRecent().args[4]?.difficulty).toBe(4);
  });

  it('renders difficulty as five stars', () => {
    const { picker } = setup();

    expect(picker.difficultyStars(2)).toBe('★★☆☆☆');
    expect(picker.difficultyStars(null)).toBe('');
  });
});
