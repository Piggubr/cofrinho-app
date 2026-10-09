import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { Gasto, ParDeTokens, ResumoDoMes } from '../core/api/models';
import { mesKey } from '../core/ui/datas';
import { apiDaDemo, reiniciarDemo } from './api-da-demo';

/** A API falsa da demonstracao responde como o backend e guarda as escritas em memoria. */
describe('API da demonstracao', () => {
  let http: HttpClient;
  const mes = mesKey(new Date());

  beforeEach(() => {
    reiniciarDemo();
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([apiDaDemo]))] });
    http = TestBed.inject(HttpClient);
  });

  const get = <T>(url: string, params: Record<string, string> = {}) => firstValueFrom(http.get<T>(url, { params }));
  const post = <T>(url: string, corpo: unknown, params: Record<string, string> = {}) =>
    firstValueFrom(http.post<T>(url, corpo, { params }));

  it('entra sem login como a titular Premium da Família Demo', async () => {
    const tokens = await post<ParDeTokens>('/api/auth/refresh', {});
    expect(tokens.usuario.plano).toBe('PREMIUM');
    expect(tokens.usuario.role).toBe('TITULAR');
    expect((await get<{ nome: string }>('/api/family')).nome).toBe('Família Demo');
  });

  it('o resumo do mes fecha a conta e lancar um gasto muda o total', async () => {
    const antes = await get<ResumoDoMes>('/api/reports/month', { mes });
    expect(antes.sobra).toBeCloseTo(antes.receitas - antes.gastos, 2);

    await post<Gasto[]>('/api/expenses', { data: `${mes}-01`, itens: [{ item: 'Teste', valor: 100 }] });
    const depois = await get<ResumoDoMes>('/api/reports/month', { mes });
    expect(depois.gastos).toBeCloseTo(antes.gastos + 100, 2);
  });

  it('parcelado vira uma linha por mes e a regra de categoria vale', async () => {
    const linhas = await post<Gasto[]>('/api/expenses', {
      data: `${mes}-01`,
      parcelas: 3,
      itens: [{ item: 'Uber viagem', valor: 100 }],
    });
    expect(linhas.map((l) => l.valor)).toEqual([33.33, 33.33, 33.34]);
    expect(linhas[0].categoria).toBe('Transporte');
  });

  it('o ano tem 12 meses e o cartao tem fatura com o fone parcelado', async () => {
    const ano = await get<{ meses: unknown[] }>('/api/reports/year', { ano: mes.slice(0, 4) });
    expect(ano.meses.length).toBe(12);
    const contas = await get<{ id: string; tipo: string; faturaAberta: { total: number } | null }[]>('/api/accounts');
    expect(contas.find((c) => c.tipo === 'CARTAO')?.faturaAberta).not.toBeNull();
  });

  it('pagar conta fixa lanca o gasto; pagamento e Pluggy respondem que sao indisponiveis', async () => {
    const contas = await get<{ id: string; situacao: string }[]>('/api/bills', { mes });
    const pendente = contas.find((c) => c.situacao !== 'PAGA');
    if (pendente) {
      await post('/api/bills/' + pendente.id + '/pay', {}, { mes });
      const depois = await get<{ id: string; situacao: string }[]>('/api/bills', { mes });
      expect(depois.find((c) => c.id === pendente.id)?.situacao).toBe('PAGA');
    }

    const falha = await post('/api/billing/checkout', { periodo: 'MENSAL' }).catch((e: HttpErrorResponse) => e);
    expect((falha as HttpErrorResponse).status).toBe(422);
    expect((falha as HttpErrorResponse).error.erro).toContain('demonstração');
    const pluggy = await post('/api/banking/connect-token', {}).catch((e: HttpErrorResponse) => e);
    expect((pluggy as HttpErrorResponse).status).toBe(422);
  });

  it('rota desconhecida responde 404 sem sair para a rede', async () => {
    const falha = await get('/api/nao-existe').catch((e: HttpErrorResponse) => e);
    expect((falha as HttpErrorResponse).status).toBe(404);
  });
});
