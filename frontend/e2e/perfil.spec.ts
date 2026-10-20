import { expect, test } from '@playwright/test';

/** Foto de perfil: escolher no aparelho, ver no Perfil e no topo, e tirar. */

// PNG 40x20 (metade de cada cor): o app recorta o quadrado do centro e regrava em JPEG.
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAACgAAAAUCAIAAABwJOjsAAAAJ0lEQVR4nGM4YJBANqJA6wGGUYtHLR61eNTiUYtHLR61eNTikWMxAMVBGj1Ea32QAAAAAElFTkSuQmCC',
  'base64',
);

test('coloca, mostra e remove a foto de perfil', async ({ page }) => {
  await page.goto('/perfil');
  const foto = page.locator('.foto-do-perfil');
  await expect(foto.locator('.avatar-inicial')).toBeVisible();

  await page.locator('input[type="file"]').setInputFiles({
    name: 'eu.png',
    mimeType: 'image/png',
    buffer: PNG,
  });

  await expect(page.getByText('Foto trocada.')).toBeVisible();
  await expect(foto.locator('img')).toHaveAttribute('src', /^data:image\/jpeg;base64,/);
  await expect(page.locator('.usuario-link img.avatar')).toHaveAttribute(
    'src',
    /^data:image\/jpeg/,
  );

  await page.getByRole('button', { name: 'Remover foto' }).click();
  await expect(page.getByText('Foto removida.')).toBeVisible();
  await expect(foto.locator('.avatar-inicial')).toBeVisible();
});

/** Aparencia: o modo escolhido vence o do aparelho e continua depois de recarregar. */
test('escolhe o modo claro com o aparelho no escuro e volta ao automatico', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.goto('/perfil');
  const fundo = () => page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  const escuro = await fundo();

  const aparencia = page.getByRole('radiogroup', { name: 'Aparência' });
  await aparencia.getByRole('radio', { name: 'Claro' }).click();
  await expect(aparencia.getByRole('radio', { name: 'Claro' })).toHaveAttribute(
    'aria-checked',
    'true',
  );
  await expect.poll(fundo).not.toBe(escuro);
  const claro = await fundo();

  await page.reload();
  await expect.poll(fundo).toBe(claro);

  await page
    .getByRole('radiogroup', { name: 'Aparência' })
    .getByRole('radio', { name: 'Automático' })
    .click();
  await expect.poll(fundo).toBe(escuro);
});
