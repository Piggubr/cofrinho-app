// Gera os ic_*.xml de src/main/res/drawable a partir dos SVGs do Material Symbols Outlined
// (google/material-design-icons, licença Apache 2.0: os desenhos podem ser copiados e
// modificados, mantendo o aviso de licença, que vai num comentário de cada arquivo).
// Rodar de novo só quando um ícone mudar ou entrar um novo (node 18 ou mais novo, sem npm):
//   node gerar-icones.mjs
import { writeFileSync } from 'node:fs';

// Nome do drawable → nome do Material Symbol. Os ic_<chave> seguem as chaves de
// frontend/src/app/core/ui/icone.ts (desenhos do Lucide no site); o equivalente mais próximo.
const icones = {
  // Abas (frontend/src/app/layout/shell/shell.ts, SIMBOLOS).
  painel: 'home',
  gastos: 'receipt_long',
  receitas: 'arrow_circle_down',
  relatorios: 'bar_chart',
  mais: 'menu',
  // icone.ts
  casa: 'home',
  carteira: 'account_balance_wallet',
  entrada: 'trending_up',
  grafico: 'bar_chart',
  cartao: 'credit_card',
  banco: 'account_balance',
  recibo: 'receipt_long',
  pizza: 'pie_chart',
  calendario: 'calendar_today',
  carrinho: 'shopping_cart',
  local: 'location_on',
  filme: 'movie',
  imagem: 'image',
  camera: 'photo_camera',
  trofeu: 'trophy',
  alvo: 'target',
  pessoas: 'group',
  pessoa: 'person',
  estrela: 'star',
  sair: 'logout',
  busca: 'search',
  adicionar: 'add',
  fechar: 'close',
  menu: 'menu',
  grade: 'grid_view',
  esquerda: 'chevron_left',
  direita: 'chevron_right',
  lixeira: 'delete',
  lapis: 'edit',
  check: 'check',
  bandeira: 'flag',
  sacola: 'shopping_bag',
  nota: 'sticky_note_2',
  compartilhar: 'share',
  subir: 'keyboard_arrow_up',
  descer: 'keyboard_arrow_down',
  arrastar: 'drag_indicator',
  brilho: 'auto_awesome',
  enviar: 'send',
  alerta: 'warning',
  cofrinho: 'savings',
};

const base = 'https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web';

for (const [nome, simbolo] of Object.entries(icones)) {
  const resposta = await fetch(`${base}/${simbolo}/materialsymbolsoutlined/${simbolo}_24px.svg`);
  if (!resposta.ok) throw new Error(`${simbolo}: HTTP ${resposta.status}`);
  const svg = await resposta.text();
  // Um path só, em viewBox="0 -960 960 960"; alguns SVGs antigos (auto_awesome) vêm em 24x24.
  const caminhos = [...svg.matchAll(/<path d="([^"]+)"/g)].map((m) => m[1]);
  const grade = svg.includes('viewBox="0 -960 960 960"') ? 960 : !svg.includes('viewBox') ? 24 : 0;
  if (caminhos.length !== 1 || !grade) throw new Error(`${simbolo}: formato inesperado`);
  const path = `<path
            android:fillColor="@android:color/white"
            android:pathData="${caminhos[0]}" />`;
  writeFileSync(
    new URL(`./src/main/res/drawable/ic_${nome}.xml`, import.meta.url),
    `<!-- Material Symbols "${simbolo}" (Apache 2.0). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="${grade}"
    android:viewportHeight="${grade}">
${grade === 960 ? `    <group android:translateY="960">\n        ${path}\n    </group>` : `    ${path.replaceAll('\n    ', '\n')}`}
</vector>
`,
  );
}
