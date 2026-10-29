// Gera Cores.kt: um esquema Material 3 por variante, a partir das cores do site
// (frontend/src/styles.scss). Rodar de novo só quando uma cor do site mudar:
//   npm i --no-save @material/material-color-utilities@0.3.0 && node gerar-cores.mjs
import { writeFileSync } from 'node:fs';
import { argbFromHex, Hct, MaterialDynamicColors as C, SchemeVibrant } from '@material/material-color-utilities';

// primaria e primaria-contraste de cada tema do site, no claro e no escuro.
const variantes = {
  Chiclete: { claro: ['#d1006b', '#ffffff'], escuro: ['#ff5cae', '#1f0012'] },
  Azul: { claro: ['#22608f', '#ffffff'], escuro: ['#96c4e8', '#0b2033'] },
  Lilas: { claro: ['#6a3d94', '#ffffff'], escuro: ['#c4a5ec', '#1e1030'] },
  Menta: { claro: ['#1f6b5a', '#ffffff'], escuro: ['#8fd6bd', '#0d2a22'] },
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

function esquema(nome, [primaria, contraste], escuro) {
  const s = new SchemeVibrant(Hct.fromInt(argbFromHex(primaria)), escuro, 0);
  // A primária e o texto sobre ela ficam iguais aos do site; o resto sai do algoritmo.
  const fixas = { primary: argbFromHex(primaria), onPrimary: argbFromHex(contraste) };
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
  kt += `\n${esquema(`${nome}Claro`, v.claro, false)}\n${esquema(`${nome}Escuro`, v.escuro, true)}`;
}
writeFileSync(new URL('./src/main/kotlin/br/com/piggu/core/designsystem/Cores.kt', import.meta.url), kt);
