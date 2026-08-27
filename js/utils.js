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

const nomeMes = d => [
  'JANEIRO', 'FEVEREIRO', 'MARÇO', 'ABRIL', 'MAIO', 'JUNHO',
  'JULHO', 'AGOSTO', 'SETEMBRO', 'OUTUBRO', 'NOVEMBRO', 'DEZEMBRO'
][d.getMonth()];

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