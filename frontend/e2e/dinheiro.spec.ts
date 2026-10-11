import { Page, expect, test } from '@playwright/test';

/** Os caminhos do dinheiro, do jeito que a pessoa usa: painel, lancar, planejar. */

async function abrirPainel(page: Page) {
  await page.goto('/');
  await expect(page).toHaveURL(/\/painel$/);
  await expect(page.getByText('Olá, Ana')).toBeVisible();
}

test('o painel abre com a sobra do mes e o aviso de demonstracao', async ({ page }) => {
  await abrirPainel(page);

  await expect(page.getByText('Demonstração · dados de exemplo')).toBeVisible();
  await expect(page.getByText('Sobra do mês')).toBeVisible();
  await expect(page.getByText('Para ficar de olho')).toBeVisible();
});

test('lancar um gasto: aparece na lista e soma no mes', async ({ page }) => {
  await abrirPainel(page);
  await page.goto('/gastos');

  await page.locator('#item').fill('Pizza de teste');
  await page.locator('#valor').fill('42.50');
  await page.locator('#categoria').selectOption('Lazer');
  await page.getByRole('button', { name: 'Lançar', exact: true }).click();

  await expect(page.getByText('Pizza de teste').first()).toBeVisible();
  await expect(page.getByText(/R\$\s42,50/).first()).toBeVisible();
});

test('definir a meta do mes em Metas', async ({ page }) => {
  await abrirPainel(page);
  await page.goto('/metas');
  // Esperar a carga: ela preenche o limite com a meta atual e apagaria o que foi digitado.
  await expect(page.getByRole('button', { name: 'Editar' }).first()).toBeVisible();

  await page.locator('#limite').fill('5000');
  await page.getByRole('button', { name: 'Salvar meta' }).click();

  await expect(page.getByText(/de R\$\s5\.000,00/)).toBeVisible();
  await expect(page.getByText(/% usado/)).toBeVisible();
});

test('Planejar leva a Orcamentos, Metas e Contas fixas', async ({ page }) => {
  await abrirPainel(page);
  await page.goto('/planejar');

  const conteudo = page.getByRole('main');
  for (const tela of ['Orçamentos', 'Metas', 'Contas fixas']) {
    await expect(conteudo.getByRole('link', { name: new RegExp(tela) })).toBeVisible();
  }
  await conteudo.getByRole('link', { name: /Orçamentos/ }).click();
  await expect(page).toHaveURL(/\/orcamentos$/);
  await expect(page.getByRole('heading', { name: 'Orçamentos' })).toBeVisible();
});
