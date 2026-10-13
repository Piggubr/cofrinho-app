import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ConfirmarEmail } from './confirmar-email';

describe('ConfirmarEmail', () => {
  it('manda o token do link e mostra o resultado', async () => {
    const confirmarEmail = vi.fn().mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      imports: [ConfirmarEmail],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { confirmarEmail } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ token: 'abc' }) } } },
      ],
    });
    const fixture = TestBed.createComponent(ConfirmarEmail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(confirmarEmail).toHaveBeenCalledWith('abc');
    expect(fixture.nativeElement.textContent).toContain('E-mail confirmado');
  });
});
