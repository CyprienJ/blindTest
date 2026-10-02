import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { JoinSessionComponent } from './join-session.component';

describe('JoinSessionComponent', () => {
  let http: HttpTestingController;

  async function create(code: string | null = null) {
    await TestBed.configureTestingModule({
      imports: [JoinSessionComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(code ? { code } : {}) },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(JoinSessionComponent);
    fixture.autoDetectChanges();
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  afterEach(() => http.verify());

  function paste(element: HTMLElement, text: string): void {
    const clipboardData = new DataTransfer();
    clipboardData.setData('text', text);
    element
      .querySelector('input')!
      .dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true }));
  }

  it('fills every cell from a pasted code and joins the lobby', async () => {
    const fixture = await create();
    const element = fixture.nativeElement as HTMLElement;
    const button = element.querySelector<HTMLButtonElement>('button')!;
    expect(button.textContent).toContain('Enter 6 more characters');

    paste(element, 'abc-234');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.code).toBe('ABC234');
    expect(button.disabled).toBeFalse();

    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    button.click();
    http.expectOne('/api/lobbies/ABC234/participants').flush({ code: 'ABC234' });
    expect(navigate).toHaveBeenCalledWith(['/lobby', 'ABC234']);
  });

  it('prefills a shared code and reports a full or unknown lobby', async () => {
    const fixture = await create('xyz789');
    expect(fixture.componentInstance.code).toBe('XYZ789');

    fixture.componentInstance.join();
    http
      .expectOne('/api/lobbies/XYZ789/participants')
      .flush({ code: 'LOBBY_FULL' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('This lobby is full (10 players).');

    fixture.componentInstance.join();
    http
      .expectOne('/api/lobbies/XYZ789/participants')
      .flush({ code: 'LOBBY_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No open lobby uses this code.');
  });

  it('ignores characters outside the code alphabet', async () => {
    const fixture = await create();
    paste(fixture.nativeElement, '0O1I');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.code).toBe('');
    expect(fixture.nativeElement.textContent).toContain('letters and the digits 2 to 9 only');
  });
});
