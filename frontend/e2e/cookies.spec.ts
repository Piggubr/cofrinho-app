import { expect, test } from '@playwright/test';

/** Primeira visita ao site: o banner de cookies pergunta uma vez e some depois de escolher. */
test.use({ storageState: { cookies: [], origins: [] } });

test('o banner de cookies aparece na primeira visita e nao volta depois da escolha', async ({ page }) => {
  await page.goto('/privacidade');
  const banner = page.getByRole('region', { name: 'Cookies no Piggu' });
  await expect(banner).toBeVisible();

  await banner.getByRole('button', { name: 'Só os essenciais' }).click();
  await expect(banner).toBeHidden();

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Cookies e o que fica no seu aparelho' })).toBeVisible();
  await expect(banner).toBeHidden();

  await page.getByRole('button', { name: 'Mudar minha escolha de cookies' }).click();
  await expect(banner).toBeVisible();
});
