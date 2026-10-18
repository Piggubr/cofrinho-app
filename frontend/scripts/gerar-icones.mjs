// Gera os icones do app (padrao, o classico e os do Premium), cada um no claro e no escuro,
// a partir dos vetores em resources/icones e dos desenhos deste arquivo. Desenha no Chromium do Playwright, que ja e dependencia
// dos testes, e grava:
//   - iOS: um conjunto por icone em Assets.xcassets (AppIcon e AppIcon-<id>), com a versao
//     escura que o iOS 18 usa no modo escuro;
//   - Android: ic_launcher_<id> em cada mipmap (fundo e porquinho separados, icone adaptavel);
//   - previas de 128 px em public/icones-do-app para o Perfil;
//   - os icones do site (favicon, apple-touch-icon e os do manifest) com o padrao.
//
// Rodar de frontend/:  node scripts/gerar-icones.mjs
import { chromium } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..');

/**
 * Cores de cada icone. fundo: degrade de cima para baixo (ou na diagonal, com diagonal). O
 * porquinho usa os mesmos tons do tema; no escuro so o fundo muda, para o desenho continuar
 * reconhecivel. Os icones com arte trazem o proprio desenho em vez do porquinho do vetor.
 */
const ICONES = {
  // Porquinho Neon: o porquinho de sempre em rosa-choque, sobre pink e laranja.
  padrao: {
    porquinho: {
      contorno: '#5c0031',
      corpo: '#ff9fcf',
      focinho: '#ff4fa3',
      orelha: '#ff6fb5',
      sombra: '#ff86c1',
    },
    moeda: '#ffd23f',
    diagonal: true,
    claro: ['#ff2d87', '#ff9a1f'],
    escuro: ['#6b0039', '#4a1a00'],
  },
  // O padrao de antes, rosa claro. Gratis, para quem preferir.
  classico: {
    porquinho: {
      contorno: '#810d49',
      corpo: '#f8dde6',
      focinho: '#efa5c7',
      orelha: '#c9a8b4',
      sombra: '#f3cfdc',
    },
    claro: ['#fff5f8', '#f7d7e3'],
    escuro: ['#3a1428', '#1c0a14'],
  },
  azul: {
    porquinho: {
      contorno: '#174a70',
      corpo: '#dcebf7',
      focinho: '#9cc6e6',
      orelha: '#a9bccb',
      sombra: '#cfe2f1',
    },
    claro: ['#f4f9fd', '#cfe4f5'],
    escuro: ['#163450', '#0a1724'],
  },
  menta: {
    porquinho: {
      contorno: '#174f43',
      corpo: '#d8f2e8',
      focinho: '#93d4bd',
      orelha: '#a8c4ba',
      sombra: '#c8eadd',
    },
    claro: ['#f3fbf8', '#cdeee2'],
    escuro: ['#173a31', '#0a1915'],
  },
  lilas: {
    porquinho: {
      contorno: '#4e2a70',
      corpo: '#ebe0f8',
      focinho: '#c3a6e6',
      orelha: '#b8aac6',
      sombra: '#dfd0f2',
    },
    claro: ['#faf6fe', '#e3d4f6'],
    escuro: ['#2e1f45', '#150e21'],
  },
  coral: {
    porquinho: {
      contorno: '#7e3020',
      corpo: '#fbe1d9',
      focinho: '#f0a993',
      orelha: '#cdb0a7',
      sombra: '#f5d2c7',
    },
    claro: ['#fff7f4', '#f9d3c7'],
    escuro: ['#45211a', '#1d0e0b'],
  },
  mel: {
    porquinho: {
      contorno: '#5e3f06',
      corpo: '#f9ebcc',
      focinho: '#ecc778',
      orelha: '#c9b896',
      sombra: '#f1dfb5',
    },
    claro: ['#fffaf0', '#f6dfa8'],
    escuro: ['#3f3010', '#1a1407'],
  },
  vinho: {
    porquinho: {
      contorno: '#561520',
      corpo: '#f6e1e4',
      focinho: '#e3a0aa',
      orelha: '#c8aab0',
      sombra: '#ecd0d5',
    },
    claro: ['#9a3242', '#5e1723'],
    escuro: ['#3d1018', '#16070a'],
  },
  grafite: {
    porquinho: {
      contorno: '#262b35',
      corpo: '#e8ebef',
      focinho: '#b5bfcc',
      orelha: '#a3abb6',
      sombra: '#d8dde3',
    },
    claro: ['#f7f8fa', '#d3d8df'],
    escuro: ['#2c313a', '#111317'],
  },
  namorados: {
    vetor: 'porquinho-namorados.svg',
    coracoes: true,
    porquinho: {
      contorno: '#810d49',
      corpo: '#f8dde6',
      focinho: '#efa5c7',
      orelha: '#c9a8b4',
      sombra: '#f8dce5',
    },
    claro: ['#ffe3ec', '#ff9fbf'],
    escuro: ['#5a1634', '#240816'],
  },
  // P de Piggu: a letra P com cara de porquinho.
  monograma: {
    arte: monograma,
    diagonal: true,
    claro: ['#7426e0', '#ff3d96'],
    escuro: ['#2e0f66', '#6b0a3d'],
  },
  // Silhueta: cofrinho branco chapado com a moeda caindo.
  silhueta: {
    arte: silhueta,
    diagonal: true,
    claro: ['#ffa21f', '#ff2d6e'],
    escuro: ['#5c2a00', '#5c0a2a'],
  },
};

const MOEDA = '#fbefa9';

function porquinho(icone) {
  const svg = readFileSync(join(raiz, 'resources/icones', icone.vetor ?? 'porquinho.svg'), 'utf8');
  const cores = { ...icone.porquinho, moeda: icone.moeda ?? MOEDA };
  const vars = Object.entries(cores)
    .map(([papel, cor]) => `--${papel}:${cor}`)
    .join(';');
  return `data:image/svg+xml;base64,${Buffer.from(svg.replace('<svg ', `<svg style="${vars}" `)).toString('base64')}`;
}

/** Moeda dourada com o cifrao, para os icones com arte. */
function moedaDeOuro(cx, cy, r) {
  const x = (f) => cx + f * r;
  const y = (f) => cy + f * r;
  return (
    `<defs><linearGradient id="ouro" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff07a"/>` +
    `<stop offset="1" stop-color="#ffb800"/></linearGradient></defs>` +
    `<circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#ouro)" stroke="#c97a00" stroke-width="${r * 0.12}"/>` +
    `<circle cx="${cx}" cy="${cy}" r="${r * 0.7}" fill="none" stroke="#e8a200" stroke-width="${r * 0.07}"/>` +
    `<path d="M${x(0.27)} ${y(-0.27)} C${x(0.12)} ${y(-0.42)} ${x(-0.34)} ${y(-0.4)} ${x(-0.3)} ${y(-0.12)} ` +
    `C${x(-0.26)} ${y(0.08)} ${x(0.3)} ${y(-0.02)} ${x(0.3)} ${y(0.19)} ` +
    `C${x(0.3)} ${y(0.44)} ${x(-0.16)} ${y(0.44)} ${x(-0.3)} ${y(0.28)} ` +
    `M${cx} ${y(-0.52)} L${cx} ${y(0.52)}" fill="none" stroke="#a85f00" stroke-width="${r * 0.13}" ` +
    `stroke-linecap="round"/>`
  );
}

/** P de Piggu: a haste do P e a cabeca do porquinho, olhando para a direita. */
function monograma() {
  return `
    <path d="M455 300 Q500 170 655 150 Q675 150 668 172 Q640 260 610 300 Z" fill="#fff"/>
    <path d="M505 280 Q540 210 625 192 Q610 250 590 280 Z" fill="#ffb3d6"/>
    <rect x="285" y="255" width="150" height="590" rx="75" fill="#fff"/>
    <circle cx="545" cy="455" r="225" fill="#fff"/>
    <ellipse cx="760" cy="480" rx="78" ry="98" fill="#ffc2de"/>
    <ellipse cx="745" cy="440" rx="15" ry="22" fill="#d1006b"/>
    <ellipse cx="745" cy="522" rx="15" ry="22" fill="#d1006b"/>
    <circle cx="590" cy="380" r="34" fill="#3a0d6e"/><circle cx="601" cy="368" r="11" fill="#fff"/>
    <ellipse cx="610" cy="520" rx="44" ry="26" fill="#ff3d96" opacity="0.35"/>`;
}

/** Silhueta: o cofrinho branco de lado, com a moeda caindo na fenda. */
function silhueta() {
  const corte = '#ff6a2b';
  return `
    ${moedaDeOuro(575, 225, 82)}
    <g fill="#fff">
      <ellipse cx="545" cy="580" rx="300" ry="225"/>
      <rect x="185" y="505" width="140" height="150" rx="52"/>
      <path d="M520 375 Q470 255 335 245 Q312 245 318 268 Q345 360 395 410 Z"/>
      <rect x="320" y="700" width="92" height="150" rx="38"/>
      <rect x="450" y="720" width="92" height="135" rx="38"/>
      <rect x="600" y="720" width="92" height="135" rx="38"/>
      <rect x="715" y="690" width="92" height="155" rx="38"/>
    </g>
    <path d="M838 540 q60 -10 55 -60 q-5 -40 -40 -30 q-30 10 -10 40" stroke="#fff" stroke-width="28"
      fill="none" stroke-linecap="round"/>
    <rect x="510" y="368" width="135" height="28" rx="14" fill="${corte}"/>
    <circle cx="378" cy="520" r="26" fill="#3a1200"/>
    <ellipse cx="228" cy="580" rx="15" ry="26" fill="${corte}"/>
    <ellipse cx="282" cy="580" rx="15" ry="26" fill="${corte}"/>`;
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
 * Pagina com o desenho. modo: 'inteiro' (fundo + porquinho), 'fundo', 'frente' (so o
 * porquinho, menor, dentro da area segura do icone adaptavel do Android) ou 'mascara' (fundo
 * com o porquinho menor, para o icone maskable do site).
 */
function pagina(icone, tema, modo, lado) {
  const [cima, baixo] = icone[tema];
  const fim = icone.diagonal ? 'x2="1" y2="1"' : 'x2="0" y2="1"';
  const fundo =
    modo === 'frente'
      ? ''
      : `<defs><linearGradient id="g" x1="0" y1="0" ${fim}><stop offset="0" stop-color="${cima}"/>` +
        `<stop offset="1" stop-color="${baixo}"/></linearGradient></defs>` +
        `<rect width="1024" height="1024" fill="url(#g)"/>` +
        (icone.coracoes ? coracoes(tema === 'claro' ? '#ffffff99' : '#ff7aa866') : '');
  // Porquinho: 445x381 no vetor. Inteiro ocupa ~70% da largura; no adaptavel, ~56%.
  const largura = modo === 'frente' || modo === 'mascara' ? 570 : 720;
  const altura = (largura * 381.39) / 445.46;
  const desenho =
    modo === 'fundo'
      ? ''
      : icone.arte
        ? // A arte ocupa a tela toda; no adaptavel encolhe na mesma proporcao do porquinho.
          `<g transform="translate(512 512) scale(${largura / 720}) translate(-512 -512)">${icone.arte()}</g>`
        : `<image href="${porquinho(icone)}" x="${(1024 - largura) / 2}" y="${(1024 - altura) / 2 + 12}" ` +
          `width="${largura}" height="${altura}"/>`;
  return (
    `<html><body style="margin:0;background:transparent">` +
    `<svg xmlns="http://www.w3.org/2000/svg" width="${lado}" height="${lado}" viewBox="0 0 1024 1024">` +
    `${fundo}${desenho}</svg></body></html>`
  );
}

/** Junta PNGs num .ico (o formato aceita PNG dentro desde o Windows Vista). */
function ico(imagens) {
  const cabecalho = Buffer.alloc(6 + 16 * imagens.length);
  cabecalho.writeUInt16LE(0, 0);
  cabecalho.writeUInt16LE(1, 2);
  cabecalho.writeUInt16LE(imagens.length, 4);
  let deslocamento = cabecalho.length;
  imagens.forEach(([lado, png], i) => {
    const entrada = 6 + 16 * i;
    cabecalho.writeUInt8(lado % 256, entrada);
    cabecalho.writeUInt8(lado % 256, entrada + 1);
    cabecalho.writeUInt16LE(1, entrada + 4);
    cabecalho.writeUInt16LE(32, entrada + 6);
    cabecalho.writeUInt32LE(png.length, entrada + 8);
    cabecalho.writeUInt32LE(deslocamento, entrada + 12);
    deslocamento += png.length;
  });
  return Buffer.concat([cabecalho, ...imagens.map(([, png]) => png)]);
}

async function main() {
  const navegador = await chromium.launch();
  const aba = await navegador.newPage({ deviceScaleFactor: 1 });

  async function desenhar(
    icone,
    tema,
    modo,
    lado,
    destino,
    { redondo = false, cantos = false } = {},
  ) {
    await aba.setViewportSize({ width: lado, height: lado });
    await aba.setContent(pagina(icone, tema, modo, lado));
    if (redondo || cantos) {
      await aba.addStyleTag({ content: `svg{border-radius:${redondo ? '50%' : '22%'}}` });
    }
    // O icone do iOS nao pode ter transparencia; o do Android (frente e redondo) precisa.
    const opaco = modo !== 'frente' && !redondo && !cantos && !destino?.includes('/res/');
    const png = await aba.screenshot({ omitBackground: !opaco });
    if (destino) {
      mkdirSync(dirname(destino), { recursive: true });
      writeFileSync(destino, png);
    }
    return png;
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
      await desenhar(
        icone,
        'claro',
        'inteiro',
        Math.round(48 * fator),
        join(dir, `${nome}_round.png`),
        {
          redondo: true,
        },
      );
      await desenhar(
        icone,
        'claro',
        'fundo',
        Math.round(108 * fator),
        join(dir, `${nome}_background.png`),
      );
      await desenhar(
        icone,
        'claro',
        'frente',
        Math.round(108 * fator),
        join(dir, `${nome}_foreground.png`),
      );
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

  // Site: favicon com cantos arredondados; os outros quadrados, que o sistema recorta.
  const site = join(raiz, 'public/icons');
  await desenhar(ICONES.padrao, 'claro', 'inteiro', 180, join(site, 'apple-touch-icon.png'));
  await desenhar(ICONES.padrao, 'claro', 'inteiro', 192, join(site, 'icon-192.png'));
  await desenhar(ICONES.padrao, 'claro', 'inteiro', 512, join(site, 'icon-512.png'));
  await desenhar(ICONES.padrao, 'claro', 'mascara', 512, join(site, 'icon-maskable-512.png'));
  const favicons = [];
  for (const lado of [16, 32, 48, 64]) {
    // So o de 16 e o de 32 tambem ficam soltos (index.html); os maiores vao so no .ico.
    const destino = lado <= 32 ? join(site, `favicon-${lado}.png`) : null;
    favicons.push([
      lado,
      await desenhar(ICONES.padrao, 'claro', 'inteiro', lado, destino, { cantos: true }),
    ]);
  }
  writeFileSync(join(raiz, 'public/favicon.ico'), ico(favicons));

  await navegador.close();
  console.log(`Ícones gerados: ${cores.join(', ')}`);
}

await main();
