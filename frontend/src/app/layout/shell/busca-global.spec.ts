import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso } from '../../testing/auth-falso';
import { BuscaGlobal } from './busca-global';

/** Ctrl+K abre; acha telas, gastos (no servidor) e listas da familia; Enter vai para o resultado. */
describe('BuscaGlobal', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BuscaGlobal],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: new AuthFalso() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('busca em tudo e navega com Enter', () => {
    vi.useFakeTimers();
    const tela = TestBed.createComponent(BuscaGlobal);
    tela.componentRef.setInput('telas', [{ rota: '/filmes', rotulo: 'Filmes', icone: '🎬' }]);
    tela.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }));
    tela.detectChanges();
    http.expectOne('/api/places').flush([{ id: 'l1', nome: 'Café da Praça', localizacao: 'Centro', categoria: '' }]);
    http.expectOne('/api/movies').flush([{ id: 'f1', titulo: 'Cafe Society', ano: '2016' }]);
    http.expectOne((r) => r.url === '/api/shopping/items').flush([]);

    const campo = tela.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    campo.value = 'cafe';
    campo.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    http.expectOne((r) => r.url === '/api/expenses/search' && r.params.get('q') === 'cafe').flush([
      { id: 'g1', item: 'Café 500g', data: '2026-08-03', valor: 20 },
    ]);
    tela.detectChanges();

    const texto = tela.nativeElement.textContent as string;
    expect(texto).toContain('Café 500g');
    expect(texto).toContain('Café da Praça');
    expect(texto).toContain('Cafe Society');

    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(navegar).toHaveBeenCalledWith(['/gastos'], { queryParams: { mes: '2026-08' } });
  });
});
