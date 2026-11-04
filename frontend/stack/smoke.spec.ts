import { cadastrar, comToken, entrar, expect, linkDoLog, novaConta, SENHA, test } from './apoio';

/**
 * Smoke: o caminho minimo que precisa funcionar depois de cada build, com tudo de
 * verdade (imagens do Dockerfile, migracoes do Flyway, gateway, JWKS e cookie).
 * Roda com o scripts/smoke.sh; os de regressao ficam em regressao.spec.ts.
 */

test.describe('smoke', { tag: '@smoke' }, () => {
  test('cada servico esta no ar e pede login', async ({ api }) => {
    // Um endpoint de cada servico. 401 (e nao 500/502/503) prova que o gateway achou
    // o servico e que o servico subiu, leu o JWKS e recusou quem nao tem token.
    // O banking so sobe com o perfil open-finance; sem ele o painel esconde o card.
    for (const rota of [
      '/api/auth/me',
      '/api/expenses',
      '/api/coins',
      '/api/places',
      '/api/feed',
    ]) {
      const resposta = await api.get(rota);
      expect(resposta.status(), rota).toBe(401);
    }
    const jwks = await api.get('/.well-known/jwks.json');
    expect(jwks.status()).toBe(200);
    expect((await jwks.json()).keys.length).toBeGreaterThan(0);
  });

  test('cadastro, entrada e confirmacao do e-mail pelo link', async ({ api }) => {
    const conta = novaConta();
    await cadastrar(api, conta);

    const sessao = await entrar(api, conta);
    expect(sessao.usuario.email).toBe(conta.email);
    const antes = await (await api.get('/api/auth/me', { headers: comToken(sessao) })).json();
    expect(antes.emailConfirmado).toBe(false);

    const token = await linkDoLog('link de confirmacao', sessao.usuario.id);
    // Conta com senha so confirma com a propria sessao (D-152).
    const semSessao = await api.post('/api/auth/confirmar-email', { data: { token } });
    expect(semSessao.status()).toBe(403);
    const confirmar = await api.post('/api/auth/confirmar-email', {
      data: { token },
      headers: comToken(sessao),
    });
    expect(confirmar.status()).toBe(204);

    const depois = await (await api.get('/api/auth/me', { headers: comToken(sessao) })).json();
    expect(depois.emailConfirmado).toBe(true);
    // O link vale uma vez so.
    const deNovo = await api.post('/api/auth/confirmar-email', {
      data: { token },
      headers: comToken(sessao),
    });
    expect(deNovo.status()).toBe(422);
  });

  test('com a sessao, cada servico responde e o gasto lancado aparece na lista', async ({
    api,
  }) => {
    const conta = novaConta();
    await cadastrar(api, conta);
    const headers = comToken(await entrar(api, conta));

    for (const rota of ['/api/coins', '/api/places', '/api/feed', '/api/categories']) {
      const resposta = await api.get(rota, { headers });
      expect(resposta.status(), rota).toBe(200);
    }

    const hoje = new Date().toISOString().slice(0, 10);
    const lancar = await api.post('/api/expenses', {
      headers,
      data: {
        data: hoje,
        estabelecimento: 'Padaria do smoke',
        origem: 'Manual',
        itens: [{ item: 'Pao de queijo', categoria: 'Mercado', valor: 12.5, tipo: 'Variavel' }],
      },
    });
    expect(lancar.status(), await lancar.text()).toBe(201);
    const [gasto] = await lancar.json();

    const lista = await (
      await api.get(`/api/expenses?mes=${hoje.slice(0, 7)}`, { headers })
    ).json();
    expect(lista.map((g: { id: string }) => g.id)).toContain(gasto.id);

    expect((await api.delete(`/api/expenses/${gasto.id}`, { headers })).status()).toBe(204);
    const semEle = await (
      await api.get(`/api/expenses?mes=${hoje.slice(0, 7)}`, { headers })
    ).json();
    expect(semEle.map((g: { id: string }) => g.id)).not.toContain(gasto.id);
  });

  test('a sessao renova pelo cookie e cai depois de sair', async ({ api }) => {
    const conta = novaConta();
    await cadastrar(api, conta);
    await entrar(api, conta);

    const renovar = await api.post('/api/auth/refresh');
    expect(renovar.status()).toBe(200);
    const nova = await renovar.json();
    expect(
      (
        await api.get('/api/auth/me', { headers: { Authorization: `Bearer ${nova.accessToken}` } })
      ).status(),
    ).toBe(200);

    expect((await api.post('/api/auth/logout')).status()).toBe(204);
    expect((await api.post('/api/auth/refresh')).status()).toBe(401);
  });

  test('trocar a senha no Perfil: a antiga para de valer e a nova entra', async ({ api }) => {
    const conta = novaConta();
    await cadastrar(api, conta);
    const sessao = await entrar(api, conta);

    const novaSenha = 'OutraFofura2026';
    const trocar = await api.put('/api/auth/me/senha', {
      headers: comToken(sessao),
      data: { senhaAtual: SENHA, novaSenha },
    });
    expect(trocar.status(), await trocar.text()).toBe(200);

    const comAntiga = await api.post('/api/auth/entrar', {
      data: { email: conta.email, senha: SENHA },
    });
    expect(comAntiga.status()).toBe(401);
    await entrar(api, { ...conta, senha: novaSenha });
  });

  // "Esqueci minha senha": POST /api/auth/senha/esqueci e /senha/redefinir; em dev o link
  // de 1 hora sai no log do identity.
  test('esqueci a senha: pedir o link, redefinir e entrar com a nova', async ({ api }) => {
    const conta = novaConta();
    await cadastrar(api, conta);
    const sessao = await entrar(api, conta);

    const pedir = await api.post('/api/auth/senha/esqueci', { data: { email: conta.email } });
    expect(pedir.status()).toBe(202);
    // E-mail sem conta: a mesma resposta, para nao contar quem tem conta.
    const semConta = await api.post('/api/auth/senha/esqueci', {
      data: { email: novaConta().email },
    });
    expect(semConta.status()).toBe(202);

    const token = await linkDoLog('link de nova senha', sessao.usuario.id);
    const novaSenha = 'SenhaNova2026ok';
    const redefinir = await api.post('/api/auth/senha/redefinir', { data: { token, novaSenha } });
    expect(redefinir.status(), await redefinir.text()).toBe(204);

    // A senha antiga para de valer, a nova entra e a sessao que estava aberta cai.
    const comAntiga = await api.post('/api/auth/entrar', {
      data: { email: conta.email, senha: SENHA },
    });
    expect(comAntiga.status()).toBe(401);
    expect((await api.post('/api/auth/refresh')).status()).toBe(401);
    await entrar(api, { ...conta, senha: novaSenha });

    // O link vale uma vez so.
    const deNovo = await api.post('/api/auth/senha/redefinir', {
      data: { token, novaSenha: 'OutraNova2026ok' },
    });
    expect(deNovo.status()).toBe(422);
  });

  test('pela tela: criar conta, confirmar o e-mail, entrar e ver o painel', async ({ page }) => {
    const conta = novaConta('Fofinha');

    await page.goto('/entrar');
    await page.getByRole('tab', { name: 'Criar conta' }).click();
    await page.getByLabel('Como quer ser chamado?').fill(conta.nome);
    await page.getByLabel('Data de nascimento').fill('1995-05-10');
    await page.getByLabel('E-mail').fill(conta.email);
    await page.getByLabel('Senha', { exact: true }).fill(conta.senha);
    await page.getByLabel('Repita a senha').fill(conta.senha);
    await page.locator('#telefone-cadastro').fill('11 98765-4321');
    await page.getByLabel(/^CPF/).fill('529.982.247-25');
    await page.locator('.aceite-do-cadastro input[type=checkbox]').check();
    await page.getByRole('button', { name: 'Criar minha conta' }).click();
    await expect(page.getByRole('status')).toContainText('Confirme seu e-mail');

    await page.getByLabel('E-mail').fill(conta.email);
    await page.getByLabel('Senha').fill(conta.senha);
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    await expect(page).toHaveURL(/\/painel$/);
    // Regressao de 16/10: o cumprimento usava a parte do e-mail antes do @.
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(`Olá, ${conta.nome}`);

    const id = await page.evaluate(async () => {
      const resposta = await fetch('/api/auth/refresh', { method: 'POST' });
      return (await resposta.json()).usuario.id as string;
    });
    const token = await linkDoLog('link de confirmacao', id);
    await page.goto(`/confirmar-email?token=${token}`);
    await expect(page.getByRole('heading', { name: 'E-mail confirmado' })).toBeVisible();
  });
});
