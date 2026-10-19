import { arquivo, cadastrar, entrar, expect, novaConta, pedidoDeCadastro, test } from './apoio';

/**
 * Regressao: cada teste trava um problema que ja aconteceu (ou que uma revisao de
 * seguranca fechou) e que os testes de cada servico, isolados, nao pegariam.
 * Novo problema corrigido? Um teste aqui, com a data e o que quebrou.
 */

test.describe('regressao', { tag: '@regressao' }, () => {
  test('14/10: as rotas de entrar e cadastrar respondem sem token (o login dava 401)', async ({
    api,
  }) => {
    // O front chamava /api/auth/entrar e /cadastro e o backend respondia 401: a rota
    // nao estava liberada. Corpo vazio tem que cair na validacao, nunca na seguranca.
    for (const rota of [
      '/api/auth/entrar',
      '/api/auth/cadastro',
      '/api/auth/google',
      '/api/auth/confirmar-email',
    ]) {
      const resposta = await api.post(rota, { data: {} });
      expect(resposta.status(), rota).toBe(400);
    }
  });

  test('13/10: cadastro e login nao contam quais e-mails tem conta', async ({ api }) => {
    const conta = novaConta();
    await cadastrar(api, conta);

    // Cadastro repetido: a mesma resposta de um cadastro novo.
    const repetido = await api.post('/api/auth/cadastro', { data: pedidoDeCadastro(conta) });
    expect(repetido.status()).toBe(202);
    expect(await repetido.text()).toBe('');

    // Senha errada e e-mail sem conta: mesma resposta, palavra por palavra.
    const senhaErrada = await api.post('/api/auth/entrar', {
      data: { email: conta.email, senha: 'Errada2026x' },
    });
    const semConta = await api.post('/api/auth/entrar', {
      data: { email: novaConta().email, senha: 'Errada2026x' },
    });
    expect(senhaErrada.status()).toBe(401);
    expect(semConta.status()).toBe(401);
    expect((await semConta.json()).erro).toBe((await senhaErrada.json()).erro);
  });

  test('o refresh token so viaja no cookie HttpOnly, nunca no corpo', async ({ api }) => {
    const conta = novaConta();
    await cadastrar(api, conta);
    const resposta = await api.post('/api/auth/entrar', {
      data: { email: conta.email, senha: conta.senha, lembrar: true },
    });
    expect(resposta.status()).toBe(200);

    const corpo = await resposta.json();
    expect(Object.keys(corpo)).not.toContain('refreshToken');
    const cookie =
      resposta.headersArray().find((h) => h.name.toLowerCase() === 'set-cookie')?.value ?? '';
    expect(cookie).toMatch(/^piggu_refresh=[\w-]{20,}/);
    for (const atributo of ['HttpOnly', 'Secure', 'SameSite=Strict', 'Path=/api/auth']) {
      expect(cookie, atributo).toContain(atributo);
    }
  });

  test('o gateway nao expoe as rotas internas nem o painel do actuator', async ({ api }) => {
    // /actuator/gateway lista as rotas e aceita POST para criar outras, sem login.
    expect((await api.get('/actuator/gateway/routes')).status()).toBe(404);
    expect((await api.post('/actuator/gateway/refresh')).status()).not.toBe(200);
    expect((await api.get('/actuator/env')).status()).toBe(404);
    // /api/meus-dados e so entre servicos (exportar e apagar a conta).
    expect((await api.get('/api/meus-dados')).status()).toBe(404);
  });

  test('CORS so libera a origem do site, com credenciais', async ({ api }) => {
    const permitida = await api.fetch('/api/auth/entrar', {
      method: 'OPTIONS',
      headers: { Origin: 'http://localhost:4400', 'Access-Control-Request-Method': 'POST' },
    });
    expect(permitida.headers()['access-control-allow-origin']).toBe('http://localhost:4400');
    expect(permitida.headers()['access-control-allow-credentials']).toBe('true');

    const estranha = await api.fetch('/api/auth/entrar', {
      method: 'OPTIONS',
      headers: {
        Origin: 'https://site-malicioso.example',
        'Access-Control-Request-Method': 'POST',
      },
    });
    expect(estranha.headers()['access-control-allow-origin']).toBeUndefined();
  });

  test('16/10: o client do Google do front e o mesmo do backend', async () => {
    // O login do Google deu 401 invalid_client em producao e o client foi trocado
    // (f15f856). Front e backend com clients diferentes: o backend recusa o token.
    const doFront = /googleClientId: '([^']+)'/.exec(
      arquivo('frontend/src/app/core/config/app-config.ts'),
    )?.[1];
    const doBackend = /^GOOGLE_CLIENT_ID=(.+)$/m.exec(arquivo('backend/.env.example'))?.[1];
    expect(doFront).toBeTruthy();
    expect(doFront).toBe(doBackend);
  });

  test('a versao dos Termos e a mesma no front e no backend', async ({ api }) => {
    // O backend recusa consentimento com versao diferente (Consentimentos.VERSAO_DO_AVISO):
    // um front com a versao velha nao consegue autorizar a leitura de nota nem o assistente.
    const doFront = /VERSAO_DO_AVISO = '([^']+)'/.exec(
      arquivo('frontend/src/app/core/privacidade/aviso.ts'),
    )?.[1];
    const doBackend = /VERSAO_DO_AVISO = "([^"]+)"/.exec(
      arquivo('backend/piggu-common/src/main/java/com/piggu/common/dados/Consentimentos.java'),
    )?.[1];
    expect(doFront).toBeTruthy();
    expect(doFront).toBe(doBackend);

    // E quem acabou de se cadastrar nao cai na tela de reaceite.
    const conta = novaConta();
    await cadastrar(api, conta);
    expect((await entrar(api, conta)).usuario.termosAceitos).toBe(doFront);
  });
});
