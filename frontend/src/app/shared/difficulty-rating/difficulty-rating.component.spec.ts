import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DifficultyRatingComponent } from './difficulty-rating.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, DifficultyRatingComponent],
  template: '<app-difficulty-rating [formControl]="control" />',
})
class HostComponent {
  readonly control = new FormControl<number | null>(null);
}

describe('DifficultyRatingComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const stars = (): HTMLButtonElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll('button[role="radio"]'));
    return { fixture, host: fixture.componentInstance, stars };
  }

  it('writes the selected level to the form control and marks it touched', () => {
    const { fixture, host, stars } = setup();

    stars()[3].click();
    fixture.detectChanges();

    expect(host.control.value).toBe(4);
    expect(host.control.touched).toBeTrue();
    expect(stars().filter((star) => star.classList.contains('filled')).length).toBe(4);
    expect(stars()[3].getAttribute('aria-checked')).toBe('true');
  });

  it('renders values written by the form and follows the disabled state', () => {
    const { fixture, host, stars } = setup();

    host.control.setValue(2);
    host.control.disable();
    fixture.detectChanges();

    expect(stars().filter((star) => star.classList.contains('filled')).length).toBe(2);
    expect(stars().every((star) => star.disabled)).toBeTrue();
  });
});
