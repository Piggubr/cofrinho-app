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

/** Moeda: cada escolha salva na hora; a cotacao do topo aparece e some com a chave. */
test('liga e desliga a cotacao do topo sem botao de salvar', async ({ page, isMobile }) => {
  // No celular o topo nao tem espaco para a cotacao; a escolha do par aparece igual.
  await page.goto('/perfil');
  const cartao = page.locator('section[aria-labelledby="titulo-moeda"]');
  await expect(cartao.getByRole('button')).toHaveCount(0);
  await expect(cartao.getByLabel('Cotação entre')).toHaveCount(0);

  await cartao.getByRole('checkbox', { name: 'Mostrar a cotação no topo do app' }).check();
  await expect(cartao.getByLabel('Cotação entre')).toHaveValue('BRL');
  await expect(cartao.getByLabel('para', { exact: true })).toHaveValue('USD');
  if (!isMobile) {
    // A demo tem 1 USD a R$ 5,50: na ordem escolhida, BRL primeiro, com 4 casas.
    await expect(page.locator('.cotacao')).toHaveText(/1 BRL = US\$\s0,1818/);
  }

  // A moeda do app nao mexe no par da cotacao.
  await cartao.getByLabel('Moeda do app').selectOption('EUR');
  await expect(cartao.getByLabel('Cotação entre')).toHaveValue('BRL');
  if (!isMobile) {
    await expect(page.locator('.cotacao')).toHaveText(/1 BRL = US\$\s0,1818/);
  }

  await cartao.getByRole('checkbox', { name: 'Mostrar a cotação no topo do app' }).uncheck();
  await expect(page.locator('.cotacao')).toHaveCount(0);
  await expect(cartao.getByLabel('Cotação entre')).toHaveCount(0);
});

/** Organizar o Painel: arrastar e soltar, com o mouse no computador e o dedo no celular. */
test('arrasta o Girls Math para o topo do Painel', async ({ page, isMobile }) => {
  await page.goto('/perfil#organizar-painel');
  const lista = page.locator('.ordem-do-painel');
  const nomes = lista.locator('.nome-do-bloco');
  await expect(nomes.first()).toHaveText('Resumo do mês');
  await expect(lista.getByRole('button', { name: /^Subir/ })).toHaveCount(0);

  const linha = lista.locator('li', { hasText: 'Girls Math' });
  await linha.scrollIntoViewIfNeeded();
  const alca = await linha.locator('.alca').boundingBox();
  const topo = await lista.locator('li').first().boundingBox();
  const x = alca!.x + alca!.width / 2;
  const de = alca!.y + alca!.height / 2;
  const ate = topo!.y + 4;

  if (isMobile) {
    // Toque de verdade (pointerType "touch"), passo a passo como um dedo.
    const cdp = await page.context().newCDPSession(page);
    const tocar = (type: string, y: number) =>
      cdp.send('Input.dispatchTouchEvent', {
        type,
        touchPoints: type === 'touchEnd' ? [] : [{ x, y }],
      });
    await tocar('touchStart', de);
    for (let passo = 1; passo <= 10; passo++) {
      await tocar('touchMove', de + ((ate - de) * passo) / 10);
    }
    await tocar('touchEnd', ate);
  } else {
    await page.mouse.move(x, de);
    await page.mouse.down();
    await page.mouse.move(x, ate, { steps: 10 });
    await page.mouse.up();
  }

  await expect(nomes.first()).toHaveText('Girls Math');
  await expect(nomes.nth(1)).toHaveText('Resumo do mês');
  // Fica guardado no aparelho.
  await page.reload();
  await expect(page.locator('.ordem-do-painel .nome-do-bloco').first()).toHaveText('Girls Math');
});
