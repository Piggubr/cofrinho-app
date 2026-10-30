// Gera Cores.kt: um esquema Material 3 por variante, a partir das cores do site
// (frontend/src/styles.scss). Rodar de novo só quando uma cor do site mudar, com a
// biblioteca numa pasta temporária (nada de node_modules no repo):
//   d=$(mktemp -d) && npm i --prefix "$d" @material/material-color-utilities@0.3.0 &&
//   cp gerar-cores.mjs "$d" && ln -s "$PWD/src" "$d/src" && node "$d/gerar-cores.mjs"
import { writeFileSync } from 'node:fs';
import { argbFromHex, Hct, MaterialDynamicColors as C, SchemeNeutral, SchemeVibrant } from '@material/material-color-utilities';

// primaria e primaria-contraste de cada tema do site, no claro e no escuro, na ordem do
// TEMAS de frontend/src/app/core/ui/tema.ts. `texto` troca só a primary do Android quando a do
// site não dá 4,5:1 sobre o fundo e os cartões (ContrasteTest); o resto sai da cor do site.
const variantes = {
  Chiclete: { claro: ['#d1006b', '#ffffff'], escuro: ['#ff5cae', '#1f0012'], texto: { claro: '#c60065', escuro: '#ff64b2' } },
  Rosa: { claro: ['#8e2f58', '#ffffff'], escuro: ['#e58fb2', '#2a0f1d'] },
  Menta: { claro: ['#1f6b5a', '#ffffff'], escuro: ['#8fd6bd', '#0d2a22'] },
  Azul: { claro: ['#22608f', '#ffffff'], escuro: ['#96c4e8', '#0b2033'] },
  Lilas: { claro: ['#6a3d94', '#ffffff'], escuro: ['#c4a5ec', '#1e1030'] },
  Coral: { claro: ['#a8432c', '#ffffff'], escuro: ['#f0a993', '#2e120b'] },
  Mel: { claro: ['#855a0b', '#ffffff'], escuro: ['#ecc778', '#2a1d02'] },
  Vinho: { claro: ['#7a1f2e', '#ffffff'], escuro: ['#e899a4', '#2b0a10'] },
  // Grafite é cinza no site: o Vibrant puxaria as superfícies para um azul forte.
  Grafite: { claro: ['#363d4a', '#ffffff'], escuro: ['#c2cbd8', '#151a22'], Esquema: SchemeNeutral },
  Tangerina: { claro: ['#c43e00', '#ffffff'], escuro: ['#ff9a3c', '#2a1200'], texto: { claro: '#b73a00' } },
  Eletrico: { claro: ['#7426e0', '#ffffff'], escuro: ['#c69bff', '#1a0838'] },
  Turquesa: { claro: ['#007f8c', '#ffffff'], escuro: ['#3fe0d0', '#00201f'], texto: { claro: '#00707b' } },
  Cobalto: { claro: ['#2449e8', '#ffffff'], escuro: ['#8aa8ff', '#0a1240'] },
};

const papeis = [
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'inversePrimary',
  'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
  'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
  'background', 'onBackground', 'surface', 'onSurface', 'surfaceVariant', 'onSurfaceVariant',
  'surfaceTint', 'inverseSurface', 'inverseOnSurface', 'error', 'onError', 'errorContainer',
  'onErrorContainer', 'outline', 'outlineVariant', 'scrim', 'surfaceBright', 'surfaceContainer',
  'surfaceContainerHigh', 'surfaceContainerHighest', 'surfaceContainerLow', 'surfaceContainerLowest',
  'surfaceDim',
];

const hex = (argb) => `0x${(argb >>> 0).toString(16).toUpperCase().padStart(8, '0')}`;

function esquema(nome, [primaria, contraste], escuro, Esquema = SchemeVibrant, texto) {
  const s = new Esquema(Hct.fromInt(argbFromHex(primaria)), escuro, 0);
  // A primária e o texto sobre ela ficam iguais aos do site; o resto sai do algoritmo.
  const fixas = { primary: argbFromHex(texto ?? primaria), onPrimary: argbFromHex(contraste) };
  const linhas = papeis.map((p) => `    ${p} = Color(${hex(fixas[p] ?? C[p].getArgb(s))}),`);
  const fn = escuro ? 'darkColorScheme' : 'lightColorScheme';
  return `internal val ${nome} = ${fn}(\n${linhas.join('\n')}\n)\n`;
}

let kt = `// Gerado por core/designsystem/gerar-cores.mjs. Não edite à mão.
package br.com.piggu.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
`;
for (const [nome, v] of Object.entries(variantes)) {
  kt += `\n${esquema(`${nome}Claro`, v.claro, false, v.Esquema, v.texto?.claro)}\n${esquema(`${nome}Escuro`, v.escuro, true, v.Esquema, v.texto?.escuro)}`;
}
writeFileSync(new URL('./src/main/kotlin/br/com/piggu/core/designsystem/Cores.kt', import.meta.url), kt);
