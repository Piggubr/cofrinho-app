// Gera os icones do app (padrao e os do Premium), cada um no claro e no escuro, a partir
// dos vetores em resources/icones. Desenha no Chromium do Playwright, que ja e dependencia
// dos testes, e grava:
//   - iOS: um conjunto por icone em Assets.xcassets (AppIcon e AppIcon-<id>), com a versao
//     escura que o iOS 18 usa no modo escuro;
//   - Android: ic_launcher_<id> em cada mipmap (fundo e porquinho separados, icone adaptavel);
//   - previas de 128 px em public/icones-do-app para o Perfil.
//
// Rodar de frontend/:  node scripts/gerar-icones.mjs
import { chromium } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..');

/**
 * Cores de cada icone. fundo: degrade de cima para baixo. O porquinho usa os mesmos tons do
 * tema; no escuro so o fundo muda, para o desenho continuar reconhecivel.
 */
const ICONES = {
  padrao: {
    porquinho: { contorno: '#810d49', corpo: '#f8dde6', focinho: '#efa5c7', orelha: '#c9a8b4', sombra: '#f3cfdc' },
    claro: ['#fff5f8', '#f7d7e3'],
    escuro: ['#3a1428', '#1c0a14'],
  },
  azul: {
    porquinho: { contorno: '#174a70', corpo: '#dcebf7', focinho: '#9cc6e6', orelha: '#a9bccb', sombra: '#cfe2f1' },
    claro: ['#f4f9fd', '#cfe4f5'],
    escuro: ['#163450', '#0a1724'],
  },
  menta: {
    porquinho: { contorno: '#174f43', corpo: '#d8f2e8', focinho: '#93d4bd', orelha: '#a8c4ba', sombra: '#c8eadd' },
    claro: ['#f3fbf8', '#cdeee2'],
    escuro: ['#173a31', '#0a1915'],
  },
  lilas: {
    porquinho: { contorno: '#4e2a70', corpo: '#ebe0f8', focinho: '#c3a6e6', orelha: '#b8aac6', sombra: '#dfd0f2' },
    claro: ['#faf6fe', '#e3d4f6'],
    escuro: ['#2e1f45', '#150e21'],
  },
  coral: {
    porquinho: { contorno: '#7e3020', corpo: '#fbe1d9', focinho: '#f0a993', orelha: '#cdb0a7', sombra: '#f5d2c7' },
    claro: ['#fff7f4', '#f9d3c7'],
    escuro: ['#45211a', '#1d0e0b'],
  },
  mel: {
    porquinho: { contorno: '#5e3f06', corpo: '#f9ebcc', focinho: '#ecc778', orelha: '#c9b896', sombra: '#f1dfb5' },
    claro: ['#fffaf0', '#f6dfa8'],
    escuro: ['#3f3010', '#1a1407'],
  },
  vinho: {
    porquinho: { contorno: '#561520', corpo: '#f6e1e4', focinho: '#e3a0aa', orelha: '#c8aab0', sombra: '#ecd0d5' },
    claro: ['#9a3242', '#5e1723'],
    escuro: ['#3d1018', '#16070a'],
  },
  grafite: {
    porquinho: { contorno: '#262b35', corpo: '#e8ebef', focinho: '#b5bfcc', orelha: '#a3abb6', sombra: '#d8dde3' },
    claro: ['#f7f8fa', '#d3d8df'],
    escuro: ['#2c313a', '#111317'],
  },
  namorados: {
    vetor: 'porquinho-namorados.svg',
    coracoes: true,
    porquinho: { contorno: '#810d49', corpo: '#f8dde6', focinho: '#efa5c7', orelha: '#c9a8b4', sombra: '#f8dce5' },
    claro: ['#ffe3ec', '#ff9fbf'],
    escuro: ['#5a1634', '#240816'],
  },
};

const MOEDA = '#fbefa9';

function porquinho(icone) {
  const svg = readFileSync(join(raiz, 'resources/icones', icone.vetor ?? 'porquinho.svg'), 'utf8');
  const cores = { ...icone.porquinho, moeda: MOEDA };
  const vars = Object.entries(cores).map(([papel, cor]) => `--${papel}:${cor}`).join(';');
  return `data:image/svg+xml;base64,${Buffer.from(svg.replace('<svg ', `<svg style="${vars}" `)).toString('base64')}`;
}

/** Coracoezinhos espalhados no fundo do icone Namorados. */
function coracoes(cor) {
  const lugares = [
    [150, 170, 0.9, -14],
    [860, 210, 0.7, 12],
    [820, 820, 1, 10],
    [180, 800, 0.6, -8],
    [900, 520, 0.5, 0],
  ];
  return lugares
    .map(
      ([x, y, escala, giro]) =>
        `<path transform="translate(${x} ${y}) rotate(${giro}) scale(${escala})" fill="${cor}" ` +
        `d="M0 22 C-34 -6 -22 -40 0 -22 C22 -40 34 -6 0 22Z"/>`,
    )
    .join('');
}

/**
 * Pagina com o desenho. modo: 'inteiro' (fundo + porquinho), 'fundo' ou 'frente'
 * (so o porquinho, menor, dentro da area segura do icone adaptavel do Android).
 */
function pagina(icone, tema, modo, lado) {
  const [cima, baixo] = icone[tema];
  const fundo =
    modo === 'frente'
      ? ''
      : `<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${cima}"/>` +
        `<stop offset="1" stop-color="${baixo}"/></linearGradient></defs>` +
        `<rect width="1024" height="1024" fill="url(#g)"/>` +
        (icone.coracoes ? coracoes(tema === 'claro' ? '#ffffff99' : '#ff7aa866') : '');
  // Porquinho: 445x381 no vetor. Inteiro ocupa ~70% da largura; no adaptavel, ~56%.
  const largura = modo === 'frente' ? 570 : 720;
  const altura = (largura * 381.39) / 445.46;
  const desenho =
    modo === 'fundo'
      ? ''
      : `<image href="${porquinho(icone)}" x="${(1024 - largura) / 2}" y="${(1024 - altura) / 2 + 12}" ` +
        `width="${largura}" height="${altura}"/>`;
  return (
    `<html><body style="margin:0;background:transparent">` +
    `<svg xmlns="http://www.w3.org/2000/svg" width="${lado}" height="${lado}" viewBox="0 0 1024 1024">` +
    `${fundo}${desenho}</svg></body></html>`
  );
}

async function main() {
  const navegador = await chromium.launch();
  const aba = await navegador.newPage({ deviceScaleFactor: 1 });

  async function desenhar(icone, tema, modo, lado, destino, { redondo = false } = {}) {
    await aba.setViewportSize({ width: lado, height: lado });
    await aba.setContent(pagina(icone, tema, modo, lado));
    if (redondo) {
      await aba.addStyleTag({ content: 'svg{border-radius:50%}' });
    }
    mkdirSync(dirname(destino), { recursive: true });
    // O icone do iOS nao pode ter transparencia; o do Android (frente e redondo) precisa.
    const opaco = modo === 'inteiro' && !redondo && !destino.includes('/res/');
    writeFileSync(destino, await aba.screenshot({ omitBackground: !opaco }));
  }

  const ios = join(raiz, 'ios/App/App/Assets.xcassets');
  const res = join(raiz, 'android/app/src/main/res');
  const densidades = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };
  const cores = [];

  for (const [id, icone] of Object.entries(ICONES)) {
    // iOS: um tamanho so (1024) com a variacao escura.
    const conjunto = join(ios, id === 'padrao' ? 'AppIcon.appiconset' : `AppIcon-${id}.appiconset`);
    await desenhar(icone, 'claro', 'inteiro', 1024, join(conjunto, 'icone.png'));
    await desenhar(icone, 'escuro', 'inteiro', 1024, join(conjunto, 'icone-escuro.png'));
    writeFileSync(
      join(conjunto, 'Contents.json'),
      JSON.stringify(
        {
          images: [
            { filename: 'icone.png', idiom: 'universal', platform: 'ios', size: '1024x1024' },
            {
              appearances: [{ appearance: 'luminosity', value: 'dark' }],
              filename: 'icone-escuro.png',
              idiom: 'universal',
              platform: 'ios',
              size: '1024x1024',
            },
          ],
          info: { author: 'xcode', version: 1 },
        },
        null,
        2,
      ) + '\n',
    );

    // Android: o padrao continua ic_launcher; os outros ganham sufixo.
    const nome = id === 'padrao' ? 'ic_launcher' : `ic_launcher_${id}`;
    for (const [pasta, fator] of Object.entries(densidades)) {
      const dir = join(res, `mipmap-${pasta}`);
      await desenhar(icone, 'claro', 'inteiro', Math.round(48 * fator), join(dir, `${nome}.png`));
      await desenhar(icone, 'claro', 'inteiro', Math.round(48 * fator), join(dir, `${nome}_round.png`), {
        redondo: true,
      });
      await desenhar(icone, 'claro', 'fundo', Math.round(108 * fator), join(dir, `${nome}_background.png`));
      await desenhar(icone, 'claro', 'frente', Math.round(108 * fator), join(dir, `${nome}_foreground.png`));
    }
    const adaptavel =
      '<?xml version="1.0" encoding="utf-8"?>\n' +
      '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n' +
      `    <background android:drawable="@mipmap/${nome}_background"/>\n` +
      `    <foreground android:drawable="@mipmap/${nome}_foreground"/>\n` +
      '</adaptive-icon>\n';
    writeFileSync(join(res, 'mipmap-anydpi-v26', `${nome}.xml`), adaptavel);
    writeFileSync(join(res, 'mipmap-anydpi-v26', `${nome}_round.xml`), adaptavel);
    cores.push(id);

    // Previa do Perfil.
    await desenhar(icone, 'claro', 'inteiro', 128, join(raiz, 'public/icones-do-app', `${id}.png`));
  }

  // Arte de origem do Capacitor (capacitor-assets) acompanha o novo padrao.
  await desenhar(ICONES.padrao, 'claro', 'inteiro', 1024, join(raiz, 'resources/icon-only.png'));

  await navegador.close();
  console.log(`Ícones gerados: ${cores.join(', ')}`);
}

await main();
