const fmt = v => {
  const neg = v < 0;
  const partes = Math.abs(v).toFixed(2).split('.');
  const inteiro = partes[0].replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return (neg ? '- ' : '') + '€\u00A0' + inteiro + ',' + partes[1];
};

const fmtData = iso => {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}`;
};

const mesKeyDe = d =>
  d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');

const nomeMes = d => {
  const meses = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
    'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
  return meses[d.getMonth()] + ' ' + d.getFullYear();
};

const esc = valor =>
  String(valor ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

function popularSelectCategorias(sel) {
  if (!sel) return;
  sel.innerHTML = Object.keys(CATEGORIAS)
    .map(c => `<option value="${c}">${c}</option>`)
    .join('');
}

let marcadoresLugarConfigurados = [
  'Favorito',
  'Melhor custo-beneficio',
  'Voltaria',
  'Nao voltaria'
];

function aplicarConfiguracoes(configuracoes = {}) {
  const coresExtras = [
    '#E67E22', '#3498DB', '#2ECC71', '#F39C12',
    '#8E44AD', '#16A085', '#D35400', '#7F8C8D'
  ];

  const categoriasExtras = Array.isArray(configuracoes.categorias)
    ? configuracoes.categorias
    : [];

  categoriasExtras.forEach((categoria, indice) => {
    const nome = String(categoria || '').trim();
    if (nome && !Object.prototype.hasOwnProperty.call(CATEGORIAS, nome)) {
      CATEGORIAS[nome] = coresExtras[indice % coresExtras.length];
    }
  });

  const marcadoresExtras = Array.isArray(configuracoes.marcadoresLugar)
    ? configuracoes.marcadoresLugar
    : [];
  let marcadoresLocais = [];
  try { marcadoresLocais = JSON.parse(localStorage.getItem('piggu_place_tags_local') || '[]'); }
  catch (_) { marcadoresLocais = []; }

  marcadoresLugarConfigurados = [
    'Favorito',
    'Melhor custo-beneficio',
    'Voltaria',
    'Nao voltaria',
    ...marcadoresExtras.map(item => String(item || '').trim()).filter(Boolean),
    ...marcadoresLocais.map(item => String(item || '').trim()).filter(Boolean)
  ].filter((item, indice, lista) => lista.indexOf(item) === indice);

  popularSelectCategorias(document.getElementById('novaCategoria'));
  popularSelectCategorias(document.getElementById('notaCategoria'));
  renderMarcadoresLugar();
}

function renderMarcadoresLugar() {
  const container = document.getElementById('lugarMarcadores');
  if (!container) return;

  container.innerHTML = marcadoresLugarConfigurados
    .map(marcador => `
      <label class="place-tag">
        <input type="checkbox" value="${esc(marcador)}">
        <span>${esc(marcador)}</span>
      </label>
    `)
    .join('');
}
