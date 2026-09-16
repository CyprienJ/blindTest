import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminLayoutComponent } from './admin-layout.component';

describe('AdminLayoutComponent', () => {
  let fixture: ComponentFixture<AdminLayoutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminLayoutComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.detectChanges();
  });

  it('renders the administration sidebar links', () => {
    const element = fixture.nativeElement as HTMLElement;
    const hrefs = Array.from(element.querySelectorAll('.admin-sidebar a')).map((link) =>
      link.getAttribute('href'),
    );
    expect(hrefs).toEqual([
      '/admin/tracks',
      '/admin/tracks/import',
      '/admin/playlists',
      '/admin/users',
    ]);
  });
});
