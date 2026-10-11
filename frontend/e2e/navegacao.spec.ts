import { expect, test } from '@playwright/test';

/** As 5 abas do celular, o "+" de lancar e os modulos que a pessoa desliga. */

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


  test('"Mais" abre o menu agrupado e fecha ao escolher uma tela', async ({ page }) => {
    await page.goto('/painel');

    await page
      .getByRole('navigation', { name: 'Atalhos' })
      .getByRole('button', { name: 'Mais' })
      .click();
    const menu = page.getByRole('navigation', { name: 'Telas' });
    await expect(menu.locator('.menu-grupo-titulo').first()).toBeVisible();
    await menu.getByRole('link', { name: 'Receitas' }).click();

    await expect(page).toHaveURL(/\/receitas$/);
    await expect(menu).not.toHaveClass(/aberto/);
  });
});

test('desligar Filmes no perfil tira a tela do menu', async ({ page }) => {
  await page.goto('/perfil');
  // No celular o menu fica fechado (oculto); conta os itens mesmo assim.
  const item = (tela: string) => page.locator('nav.menu a.menu-item', { hasText: tela });
  await expect(item('Filmes')).toHaveCount(1);

  await page.getByRole('checkbox', { name: 'Filmes' }).uncheck();

  await expect(item('Filmes')).toHaveCount(0);
  await expect(item('Lugares')).toHaveCount(1);
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

test('o "+" abre o lancamento por cima da tela, sem sair dela', async ({ page }) => {
  await page.goto('/relatorios');

  await page.getByRole('button', { name: 'Lançar gasto ou receita' }).click();
  const painel = page.getByRole('dialog', { name: 'Lançar' });
  await painel.locator('#valor-rapido').fill('12.5');
  await painel.locator('#descricao-rapida').fill('Pão de queijo');
  await painel.getByRole('button', { name: 'Lançar gasto' }).click();

  await expect(painel.getByText('Gasto lançado.')).toBeVisible();
  await expect(page).toHaveURL(/\/relatorios$/);

  await painel.getByRole('tab', { name: 'Receita' }).click();
  await painel.locator('#valor-rapido').fill('300');
  await painel.locator('#descricao-rapida').fill('Freela');
  await painel.getByRole('button', { name: 'Lançar receita' }).click();
  await expect(painel.getByText('Receita lançada.')).toBeVisible();
});
