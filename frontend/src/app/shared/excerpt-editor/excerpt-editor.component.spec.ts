import { TestBed } from '@angular/core/testing';
import { ExcerptEditorComponent } from './excerpt-editor.component';

describe('ExcerptEditorComponent', () => {
  function setup(durationSeconds: number | null = 120) {
    const fixture = TestBed.createComponent(ExcerptEditorComponent);
    fixture.componentRef.setInput('durationSeconds', durationSeconds);
    fixture.detectChanges();
    const editor = fixture.componentInstance;
    const changes = jasmine.createSpy('selectionChange');
    editor.selectionChange.subscribe(changes);
    return { fixture, editor, changes };
  }

  it('caps the start so the whole window stays inside the track', () => {
    const { editor, changes } = setup();

    editor.setStart(100);

    expect(editor.start()).toBe(60);
    expect(editor.end()).toBe(120);
    expect(changes).toHaveBeenCalledTimes(1);
  });

  it('clamps the window to 5–60 seconds and pulls the start back when it no longer fits', () => {
    const { editor } = setup();
    editor.window.set(20);
    editor.setStart(100);

    editor.setWindow(90);
    expect(editor.window()).toBe(60);
    expect(editor.start()).toBe(60);

    editor.setWindow(2);
    expect(editor.window()).toBe(5);
    expect(editor.end()).toBe(65);
    expect(editor.selectionWidth()).toBeCloseTo((5 / 120) * 1000);
  });

  it('loops the preview back to the window start', () => {
    const { editor } = setup();
    editor.window.set(10);
    editor.setStart(30);
    const audio = { currentTime: 40 } as HTMLAudioElement;

    editor.loopWindow(audio);

    expect(audio.currentTime).toBe(30);
  });
});
