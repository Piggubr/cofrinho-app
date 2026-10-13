import { expect, test } from '@playwright/test';

/** As 5 abas do celular, o "+" de lancar e as telas que ficam sempre no menu. */

test.describe('no celular', () => {
  test.skip(({ isMobile }) => !isMobile, 'a barra de abas e o "+" sao do celular');

  test('as 5 abas levam as telas principais', async ({ page }) => {
    await page.goto('/painel');
    const abas = page.getByRole('navigation', { name: 'Atalhos' });

    await expect(abas.locator('.aba')).toHaveText([
      'Painel',
      'Gastos',
      'Relatórios',
      'Planejar',
      'Mais',
    ]);
    await abas.getByRole('link', { name: 'Relatórios' }).click();
    await expect(page).toHaveURL(/\/relatorios$/);
    await abas.getByRole('link', { name: 'Planejar' }).click();
    await expect(page).toHaveURL(/\/planejar$/);
  });


  test('"Mais" mostra so o que nao tem aba, sem rolar, e fecha ao escolher uma tela', async ({
    page,
  }) => {
    await page.goto('/painel');

    await page
      .getByRole('navigation', { name: 'Atalhos' })
      .getByRole('button', { name: 'Mais' })
      .click();
    const menu = page.getByRole('navigation', { name: 'Telas' });
    await expect(menu.getByRole('link', { name: 'Premium' })).toBeVisible();
    await expect(menu.getByRole('link', { name: 'Gastos' })).toBeHidden();
    await expect(menu.locator('.menu-grupo-titulo').first()).toBeHidden();
    const rola = await menu.evaluate((nav) => nav.scrollHeight > nav.clientHeight);
    expect(rola).toBe(false);
    await menu.getByRole('link', { name: 'Receitas' }).click();

    await expect(page).toHaveURL(/\/receitas$/);
    await expect(menu).not.toHaveClass(/aberto/);
  });

  test('o "+" se recolhe ao rolar para baixo, volta no topo e a pontinha abre o balao', async ({
    page,
  }) => {
    await page.goto('/painel');
    const mais = page.locator('button.lancar-flutuante');
    await expect(page.getByRole('heading', { name: 'Contas bancárias' })).toBeVisible();

    await page.mouse.wheel(0, 600);
    await expect(mais).toHaveClass(/recolhido/);
    await page.evaluate(() => window.scrollTo(0, 0));
    await expect(mais).not.toHaveClass(/recolhido/);

    await page.mouse.wheel(0, 600);
    await expect(mais).toHaveClass(/recolhido/);
    await expect(mais).toHaveCSS('opacity', '0.85');
    const pontinha = (await mais.boundingBox())!;
    await page.mouse.click(pontinha.x + 6, pontinha.y + pontinha.height / 2);
    await expect(mais).not.toHaveClass(/recolhido/);
    await expect(page.getByRole('menuitem', { name: 'Lançar receita' })).toBeVisible();
  });

  test('a folha do "Mais" volta ao lugar se puxada para cima e fecha se puxada para baixo', async ({
    page,
  }) => {
    await page.goto('/painel');
    const mais = page.getByRole('navigation', { name: 'Atalhos' }).getByRole('button', { name: 'Mais' });
    const menu = page.getByRole('navigation', { name: 'Telas' });
    await mais.click();
    // Espera a folha terminar de subir antes de medir onde esta a alca.
    await expect(menu).toHaveCSS('transform', 'none');
    const topo = (await menu.locator('.menu-topo').boundingBox())!;
    const [x, y] = [topo.x + 60, topo.y + 10];

    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(x, y - 200, { steps: 8 });
    await page.mouse.up();
    await expect(menu).toHaveClass(/aberto/);

    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(x, y + 250, { steps: 10 });
    await page.mouse.up();
    await expect(menu).not.toHaveClass(/aberto/);
  });
});

for (const [janela, botao] of [
  ['a busca', 'Buscar (Ctrl+K)'],
  ['o assistente', 'Perguntar ao assistente'],
] as const) {
  test(`${janela} cobre a tela, prende a rolagem e fecha ao tocar fora`, async ({ page }) => {
    await page.goto('/painel');
    await page.evaluate(() => window.scrollTo(0, 400));
    await page.getByRole('button', { name: botao }).click();
    const dialogo = page.getByRole('dialog');
    await expect(dialogo).toBeVisible();
    await expect(page.locator('html')).toHaveClass(/rolagem-travada/);

    // Toque perto do canto de baixo, longe da janela: cai no fundo escuro e fecha.
    const tela = page.viewportSize()!;
    await page.mouse.click(12, tela.height - 12);
    await expect(dialogo).toBeHidden();
    await expect(page.locator('html')).not.toHaveClass(/rolagem-travada/);
  });
}

test('todas as telas de estilo de vida ficam sempre no menu; o perfil nao as desliga', async ({
  page,
}) => {
  await page.goto('/perfil');
  // No celular o menu fica fechado (oculto); conta os itens mesmo assim.
  const item = (tela: string) => page.locator('nav.menu a.menu-item', { hasText: tela });
  for (const tela of ['Compras', 'Lugares', 'Filmes', 'Fotos', 'Prêmios', 'Girls Math']) {
    await expect(item(tela)).toHaveCount(1);
  }
  await expect(page.getByRole('heading', { name: 'Organizar o Painel' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Telas do menu' })).toHaveCount(0);
});

test('pode ser instalado: manifesto com nome, icones e atalho para lancar gasto', async ({
  page,
  request,
}) => {
  await page.goto('/');
  const href = await page.locator('link[rel="manifest"]').getAttribute('href');
  expect(href).toBe('manifest.webmanifest');
  const manifesto = await (await request.get('/manifest.webmanifest')).json();
  expect(manifesto.name).toBe('Piggu');
  expect(manifesto.display).toBe('standalone');
  expect(manifesto.icons.some((i: { purpose?: string }) => i.purpose === 'maskable')).toBe(true);
  expect((await request.get('/icons/icon-192.png')).ok()).toBe(true);
});

test('o "+" abre o balao e o lancamento por cima da tela; lancou, o painel fecha e avisa', async ({
  page,
}) => {
  await page.goto('/relatorios');
  const mais = page.getByRole('button', { name: 'Lançar gasto ou receita' });
  const painel = page.getByRole('dialog', { name: 'Lançar' });

  await mais.click();
  await expect(page.getByRole('menuitem', { name: 'Foto do recibo' })).toBeVisible();
  await page.getByRole('menuitem', { name: 'Lançar gasto' }).click();
  await painel.locator('#valor-rapido').fill('12.5');
  await painel.locator('#descricao-rapida').fill('Pão de queijo');
  await painel.getByRole('button', { name: 'Lançar gasto' }).click();

  await expect(painel).toBeHidden();
  await expect(page.getByRole('status').getByText('Gasto lançado.')).toBeVisible();
  await expect(page).toHaveURL(/\/relatorios$/);

  // "Lançar receita" no balao ja abre o painel na aba Receita.
  await mais.click();
  await page.getByRole('menuitem', { name: 'Lançar receita' }).click();
  await expect(painel.getByRole('tab', { name: 'Receita' })).toHaveAttribute('aria-selected', 'true');
  await painel.locator('#valor-rapido').fill('300');
  await painel.locator('#descricao-rapida').fill('Freela');
  await painel.getByRole('button', { name: 'Lançar receita' }).click();
  await expect(painel).toBeHidden();
  await expect(page.getByRole('status').getByText('Receita lançada.')).toBeVisible();
});

test('o tour das funcoes principais abre pelo perfil e leva ao "+"', async ({ page }) => {
  await page.goto('/perfil');
  await page.getByRole('button', { name: 'Ver o tour de novo' }).click();

  const tour = page.getByRole('dialog', { name: 'Bem-vindo ao Piggu' });
  await expect(tour).toBeVisible();
  await tour.getByRole('button', { name: 'Próximo' }).click();
  await expect(page.getByRole('dialog', { name: 'Lance pelo +' })).toBeVisible();
  await expect(page.locator('button.lancar-flutuante')).toHaveClass(/destaque/);
  // O balao sai do proprio botao: o recorte fica em volta do "+".
  await expect(page.locator('app-tutorial .recorte')).toBeVisible();
  await expect(page.locator('app-tutorial .balao')).toHaveClass(/acima/);

  for (let i = 0; i < 6; i++) {
    await page.getByRole('dialog').getByRole('button', { name: 'Próximo' }).click();
  }
  await page.getByRole('button', { name: 'Lançar meu primeiro gasto' }).click();

  await expect(page.locator('app-tutorial')).toHaveCount(0);
  await expect(page.locator('#valor-rapido')).toBeVisible();
  await expect(page).toHaveURL(/\/perfil$/);
});
