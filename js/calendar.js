function renderCalendar() {
  const grid = document.getElementById('calGrid');
  if (!grid) return;

  const ano = viewDate.getFullYear();
  const mes = viewDate.getMonth();

  const primeiroDiaMes = new Date(ano, mes, 1);
  const diasNoMes = new Date(ano, mes + 1, 0).getDate();
  const diaSemanaInicio = primeiroDiaMes.getDay();

  const hoje = new Date();
  const ehHoje = d =>
    d.getDate() === hoje.getDate() &&
    d.getMonth() === hoje.getMonth() &&
    d.getFullYear() === hoje.getFullYear();

  const cells = [];
  for (let i = 0; i < diaSemanaInicio; i++) {
    cells.push('<div class="cal-cell empty-cell"></div>');
  }

  for (let dia = 1; dia <= diasNoMes; dia++) {
    const dataIso = `${ano}-${String(mes + 1).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
    const temConta = contas.some(c => c.dia === dia);
    const temEvento = eventos.some(e => e.data === dataIso);

    const classes = ['cal-cell'];
    if (ehHoje(new Date(ano, mes, dia))) classes.push('today');

    const dots = [];
    if (temConta) {
      const pago = pagamentos.has(`${dataIso}-conta`);
      dots.push(`<span class="dot conta${pago ? ' paid' : ''}"></span>`);
    }
    if (temEvento) {
      const ev = eventos.find(e => e.data === dataIso);
      dots.push(`<span class="dot evento${ev?.pago ? ' paid' : ''}"></span>`);
    }

    cells.push(`
      <div class="${classes.join(' ')}">
        <span class="cal-daynum">${dia}</span>
        <div class="cal-dots">${dots.join('')}</div>
      </div>
    `);
  }

  grid.innerHTML = cells.join('');
}