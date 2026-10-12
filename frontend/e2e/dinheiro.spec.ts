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

test('definir o limite do mes no Orcamento', async ({ page }) => {
  await abrirPainel(page);
  await page.goto('/orcamentos');
  await expect(page.getByRole('heading', { name: 'Orçamento', exact: true })).toBeVisible();
  // Esperar a carga: ela preenche o limite com o atual e apagaria o que foi digitado.
  await expect(page.getByRole('button', { name: 'Editar' }).first()).toBeVisible();

  await page.locator('#limite').fill('5000');
  await page.getByRole('button', { name: 'Salvar limite' }).click();

  await expect(page.getByText(/de R\$\s5\.000,00/)).toBeVisible();
  await expect(page.getByText(/% usado/)).toBeVisible();
  await expect(page.getByRole('heading', { name: /Por categoria/ })).toBeVisible();
});

test('Planejar leva ao Orcamento e as Contas do mes', async ({ page }) => {
  await abrirPainel(page);
  await page.goto('/planejar');

  const conteudo = page.getByRole('main');
  for (const tela of ['Orçamento', 'Contas do mês']) {
    await expect(conteudo.getByRole('link', { name: new RegExp(tela) })).toBeVisible();
  }
  await conteudo.getByRole('link', { name: /Contas do mês/ }).click();
  await expect(page).toHaveURL(/\/contas-fixas$/);
  await expect(page.getByRole('heading', { name: 'Contas do mês' })).toBeVisible();
});

test('trazer os gastos do banco conectado, revisar e importar; de novo, nada repete', async ({
  page,
}) => {
  await abrirPainel(page);
  await page.goto('/gastos');

  await page.getByRole('button', { name: 'Trazer do banco' }).click();
  await expect(page.getByText('Padaria Pão Quente')).toBeVisible();
  await expect(page.getByText('Banco Exemplo · Conta corrente').first()).toBeVisible();
  await page.getByRole('button', { name: 'Importar marcados' }).click();
  await expect(page.getByText('Importados: 3.')).toBeVisible();

  await page.getByRole('button', { name: 'Trazer do banco' }).click();
  await expect(page.getByText(/já está no Piggu/)).toBeVisible();
});

test('cofrinho por objetivo: guardar na viagem soma nela', async ({ page }) => {
  await abrirPainel(page);
  const viagem = page.locator('.objetivo', { hasText: 'Viagem de férias' });
  await expect(viagem.getByText(/R\$\s1\.500,00 de R\$\s6\.000,00/)).toBeVisible();
  await expect(viagem.getByText(/por mês até/)).toBeVisible();

  await viagem.locator('input[type="number"]').fill('250');
  await viagem.getByRole('button', { name: 'Guardar' }).click();
  await expect(viagem.getByText(/R\$\s1\.750,00 de R\$\s6\.000,00/)).toBeVisible();
});

test('assistente: autorizar, perguntar e abrir a tela sugerida', async ({ page }) => {
  await abrirPainel(page);
  await page.getByRole('button', { name: 'Perguntar ao assistente' }).click();
  await page.getByRole('button', { name: 'Autorizo e quero perguntar' }).click();
  await page.getByRole('button', { name: 'Vou estourar algum orçamento?' }).click();

  const painel = page.getByRole('dialog', { name: 'Assistente' });
  await expect(painel.getByText('149 de 150 perguntas')).toBeVisible();
  await painel.getByRole('link', { name: 'Abrir Orçamento' }).click();
  await expect(page).toHaveURL(/\/orcamentos$/);
});

test('atalho de lancamento rapido: valor preenchido e cursor no campo', async ({ page }) => {
  await abrirPainel(page);
  await page.goto('/gastos?rapido=1&valor=18.90');
  await expect(page.locator('#valor')).toHaveValue('18.9');
  await expect(page.locator('#valor')).toBeFocused();
});

test('Minhas metas: +1 enche o anel e a meta nova entra na lista', async ({ page }) => {
  await abrirPainel(page);
  await page.goto('/metas');

  const anel = page.getByRole('meter', { name: 'Progresso das metas do mês' });
  await expect(anel).toHaveAttribute('aria-valuenow', '33');
  await page.getByRole('button', { name: 'Mais um em Ler 2 livros' }).click();
  await expect(anel).toHaveAttribute('aria-valuenow', '67');

  await page.getByRole('button', { name: '+ Criar meta' }).click();
  await page.locator('#tituloMeta').fill('Caminhar 10 vezes');
  await page.locator('#alvoMeta').fill('10');
  await page.getByRole('button', { name: 'Salvar meta' }).click();
  await expect(page.getByText('Caminhar 10 vezes')).toBeVisible();
  await expect(anel).toHaveAttribute('aria-valuenow', '50');
});

test('Girls Math: o que nao comprei soma no placar e aparece no Painel', async ({ page }) => {
  await abrirPainel(page);
  await page.goto('/girls-math');

  await page.locator('#produtoGm').fill('Jaqueta');
  await page.locator('#lojaGm').fill('Loja do centro');
  await page.locator('#valorGm').fill('150');
  await page.getByRole('button', { name: 'Guardar no Girls Math' }).click();
  await expect(page.getByText('Jaqueta')).toBeVisible();
  await expect(page.locator('.valor-grande')).toHaveText(/R\$\s499,90/);

  // Pelo app, sem recarregar: na demo recarregar volta os dados ao inicio.
  await page.getByRole('link', { name: 'Painel', exact: true }).first().click();
  // O cartao nao vem ligado: a pessoa liga em Personalizar o Painel.
  await expect(page.locator('app-girls-math-do-painel')).toHaveCount(0);
  await page.getByRole('link', { name: 'Personalizar o Painel' }).click();
  await expect(page).toHaveURL(/\/perfil#organizar-painel$/);
  await page.locator('#organizar-painel').getByRole('checkbox', { name: 'Girls Math' }).check();

  await page.getByRole('link', { name: 'Painel', exact: true }).first().click();
  await expect(page.locator('app-girls-math-do-painel')).toContainText(/R\$\s499,90/);
});
