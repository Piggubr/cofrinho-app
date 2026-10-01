let gastos = [];
let listaLancamentosExpandida = false;

function renderLista() {
  const el = document.getElementById('lista');
  if (!el) return;

  if (!gastos.length) {
    el.innerHTML = '<p class="empty">Nenhum gasto ainda.</p>';
    atualizarBotaoTodosLancamentos(0, 0);
    return;
  }

  const ordenado = [...gastos].sort((a, b) => {
    if (b.data === a.data) return b.id - a.id;
    return b.data.localeCompare(a.data);
  });

  const LIMITE_COMPACTO = 3;
  const visiveis = listaLancamentosExpandida ? ordenado : ordenado.slice(0, LIMITE_COMPACTO);

  el.innerHTML = visiveis.map(g => {
    const cor = CATEGORIAS[g.categoria] || CATEGORIAS.Outros;

    return `
      <div class="row row-editable" role="button" tabindex="0" data-gasto-id="${esc(g.id)}" aria-label="Editar ${esc(g.item)}, ${fmt(g.valor)}">
        <div class="row-left">
          <p class="row-item">${esc(g.item)}</p>
          <p class="row-date">${fmtData(g.data)}</p>
        </div>
        <div class="row-right">
          <span class="pill" style="background:${cor}26;color:${cor};">${esc(g.categoria)}</span>
          <span class="row-value">${fmt(g.valor)}</span>
        </div>
      </div>
    `;
  }).join('');

  el.querySelectorAll('[data-gasto-id]').forEach(linha => {
    linha.addEventListener('click', () => abrirEdicaoGasto(linha.dataset.gastoId));
    linha.addEventListener('keydown', evento => {
      if (evento.key !== 'Enter' && evento.key !== ' ') return;
      evento.preventDefault();
      abrirEdicaoGasto(linha.dataset.gastoId);
    });
  });

  atualizarBotaoTodosLancamentos(ordenado.length, LIMITE_COMPACTO);
}

function abrirEdicaoGasto(id) {
  const gasto = gastos.find(g => String(g.id) === String(id));
  if (!gasto) return;

  abrirModal('Editar gasto', `
    <form id="formEditarGasto" class="category-form expense-edit-form">
      <label for="editarGastoItem">O que foi?</label>
      <input id="editarGastoItem" name="item" maxlength="200" autocomplete="off" required value="${esc(gasto.item)}">
      <label for="editarGastoValor">Valor (€)</label>
      <input id="editarGastoValor" name="valor" type="number" inputmode="decimal" step="0.01" min="0" required value="${esc(gasto.valor)}">
      <label for="editarGastoCategoria">Categoria</label>
      <select id="editarGastoCategoria" name="categoria"></select>
      <p class="field-help" id="editarGastoFeedback">${esc(fmtData(gasto.data))}${gasto.estabelecimento ? ' • ' + esc(gasto.estabelecimento) : ''}</p>
      <div class="category-form-actions">
        <button class="button-secondary" id="cancelarEdicaoGasto" type="button">Cancelar</button>
        <button class="button-primary" type="submit">Salvar</button>
      </div>
      <button class="expense-delete" id="excluirGasto" type="button">Excluir gasto</button>
    </form>
  `);

  const form = document.getElementById('formEditarGasto');
  const feedback = document.getElementById('editarGastoFeedback');
  const selectCategoria = document.getElementById('editarGastoCategoria');
  const botaoSalvar = form.querySelector('button[type="submit"]');
  const botaoExcluir = document.getElementById('excluirGasto');
  let confirmandoExclusao = false;

  popularSelectCategorias(selectCategoria);
  if (!Object.prototype.hasOwnProperty.call(CATEGORIAS, gasto.categoria)) {
    selectCategoria.insertAdjacentHTML('beforeend', `<option value="${esc(gasto.categoria)}">${esc(gasto.categoria)}</option>`);
  }
  selectCategoria.value = gasto.categoria;

  document.getElementById('cancelarEdicaoGasto')?.addEventListener('click', fecharModal);

  const travar = travado => {
    botaoSalvar.disabled = travado;
    botaoExcluir.disabled = travado;
  };

  form.addEventListener('submit', async evento => {
    evento.preventDefault();
    const dados = new FormData(form);
    const item = String(dados.get('item') || '').trim();
    const valor = Number(dados.get('valor') || 0);
    const categoria = String(dados.get('categoria') || 'Outros');

    if (!item) {
      feedback.textContent = 'Digite o que foi gasto.';
      return;
    }
    if (!(valor > 0)) {
      feedback.textContent = 'Digite um valor válido.';
      return;
    }

    travar(true);
    feedback.textContent = 'Salvando…';
    try {
      await chamarAppsScript({ action: 'updateExpense', id: gasto.id, item, valor, categoria });
      await carregarDados();
      fecharModal();
    } catch (erro) {
      feedback.textContent = erro.message || 'Não foi possível salvar o gasto.';
      travar(false);
    }
  });

  botaoExcluir.addEventListener('click', async () => {
    if (!confirmandoExclusao) {
      confirmandoExclusao = true;
      botaoExcluir.textContent = 'Toque de novo para excluir';
      feedback.textContent = 'O gasto será apagado da planilha.';
      return;
    }

    travar(true);
    feedback.textContent = 'Excluindo…';
    try {
      await chamarAppsScript({ action: 'deleteExpense', id: gasto.id });
      await carregarDados();
      fecharModal();
    } catch (erro) {
      feedback.textContent = erro.message || 'Não foi possível excluir o gasto.';
      travar(false);
    }
  });
}

function atualizarBotaoTodosLancamentos(total, limite) {
  const botao = document.getElementById('btnTodosLancamentos');
  if (!botao) return;

  if (total <= limite) {
    botao.style.display = 'none';
    return;
  }

  botao.style.display = '';
  botao.textContent = listaLancamentosExpandida ? 'Ver menos' : 'Ver todos';
}

document.getElementById('btnTodosLancamentos')?.addEventListener('click', () => {
  listaLancamentosExpandida = !listaLancamentosExpandida;
  renderLista();
});

document.getElementById('btnAdicionar')?.addEventListener('click', async () => {
  const item = document.getElementById('novoItem')?.value.trim();
  const valor = Number(document.getElementById('novoValor')?.value || 0);
  const categoria = document.getElementById('novaCategoria')?.value || 'Outros';
  const tipo = document.getElementById('novoTipo')?.value || 'Variável';

  if (!item) {
    alert('Digite o que foi gasto.');
    return;
  }

  if (!(valor > 0)) {
    alert('Digite um valor válido.');
    return;
  }

  const botao = document.getElementById('btnAdicionar');
  botao.disabled = true;

  try {
    await chamarAppsScript({
      action: 'save',
      data: mesKeyDe(new Date()) + '-' + String(new Date().getDate()).padStart(2, '0'),
      origem: 'Manual',
      itens: [{ item, categoria, valor, tipo }]
    });

    document.getElementById('novoItem').value = '';
    document.getElementById('novoValor').value = '';
    await carregarDados();
  } catch (erro) {
    alert(erro.message);
  } finally {
    botao.disabled = false;
  }
});

let reciboAtual = null;

function converterArquivoParaBase64(arquivo) {
  return new Promise((resolve, reject) => {
    const leitor = new FileReader();
    leitor.onload = () => {
      const dataUrl = String(leitor.result || '');
      const [, mime, base64] = dataUrl.match(/^data:(.+?);base64,(.*)$/) || [];
      resolve({ dataUrl, mime: mime || arquivo.type || 'image/jpeg', base64 });
    };
    leitor.onerror = reject;
    leitor.readAsDataURL(arquivo);
  });
}

async function processarFotoRecibo(arquivo) {
  const preview = document.getElementById('fotoPreview');
  const hint = document.getElementById('fotoHint');
  const formManual = document.getElementById('formManual');

  const tiposPermitidos = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'];
  if (!tiposPermitidos.includes(String(arquivo.type || '').toLowerCase())) {
    alert('Escolha uma foto em JPEG, PNG, WebP ou HEIC.');
    return;
  }

  if (arquivo.size > 15 * 1024 * 1024) {
    alert('A foto é grande demais. Escolha uma imagem de até 15 MB.');
    return;
  }

  const { dataUrl, mime, base64 } = await converterArquivoParaBase64(arquivo);

  if (preview) {
    preview.src = dataUrl;
    preview.style.display = 'block';
  }
  if (hint) hint.textContent = 'Lendo o recibo...';
  if (formManual) formManual.style.display = 'none';

  try {
    const resposta = await chamarAppsScript({
      action: 'parse',
      image_base64: base64,
      mime_type: mime
    });
    reciboAtual = resposta;

    if (!resposta.itens || !resposta.itens.length) {
      if (hint) hint.textContent = 'Não achei itens nessa foto. Preenche os campos à mão.';
      if (formManual) formManual.style.display = '';
      return;
    }

    if (hint) hint.textContent = 'Confere os itens antes de salvar.';
    renderRevisaoRecibo(resposta);
  } catch (erro) {
    if (hint) hint.textContent = 'Não deu pra ler o recibo. Preenche os campos à mão.';
    if (formManual) formManual.style.display = '';
    alert(erro.message);
  }
}

function renderRevisaoRecibo(recibo) {
  const revisao = document.getElementById('revisaoRecibo');
  if (!revisao) return;

  revisao.style.display = 'block';
  revisao.innerHTML = `
    <p class="hint">${esc(recibo.estabelecimento || 'Recibo')}${recibo.data ? ' • ' + esc(fmtData(recibo.data)) : ''}</p>
    <div id="reciboItensLista"></div>
    <button class="primary" id="btnConfirmarRecibo" type="button" style="margin-top:10px;">Confirmar e salvar</button>
    <button id="btnCancelarRecibo" type="button" style="margin-top:8px;border:1px solid var(--border);border-radius:10px;width:100%;padding:8px;background:transparent;">Cancelar</button>
  `;

  const lista = document.getElementById('reciboItensLista');
  lista.innerHTML = recibo.itens.map((item, indice) => `
    <div class="recibo-item" data-index="${indice}">
      <input type="text" class="recibo-item-nome" value="${esc(item.item)}" placeholder="Item">
      <input type="number" class="recibo-item-valor" value="${item.valor}" step="0.01" placeholder="€">
      <select class="recibo-item-categoria"></select>
      <button type="button" class="recibo-item-remover" aria-label="Remover item">×</button>
    </div>
  `).join('');

  lista.querySelectorAll('.recibo-item-categoria').forEach((select, indice) => {
    popularSelectCategorias(select);
    select.value = recibo.itens[indice].categoria;
  });

  lista.querySelectorAll('.recibo-item-remover').forEach(botao => {
    botao.addEventListener('click', () => {
      botao.closest('.recibo-item').remove();
    });
  });

  document.getElementById('btnConfirmarRecibo').addEventListener('click', salvarRecibo);
  document.getElementById('btnCancelarRecibo').addEventListener('click', cancelarRecibo);
}

async function salvarRecibo() {
  const linhas = document.querySelectorAll('#reciboItensLista .recibo-item');
  const itens = Array.from(linhas).map(linha => ({
    item: linha.querySelector('.recibo-item-nome').value.trim(),
    valor: Number(linha.querySelector('.recibo-item-valor').value || 0),
    categoria: linha.querySelector('.recibo-item-categoria').value
  })).filter(item => item.item && item.valor > 0);

  if (!itens.length) {
    alert('Adicione pelo menos um item válido.');
    return;
  }

  const botao = document.getElementById('btnConfirmarRecibo');
  botao.disabled = true;

  try {
    await chamarAppsScript({
      action: 'save',
      data: reciboAtual.data,
      estabelecimento: reciboAtual.estabelecimento,
      recibo_id: reciboAtual.recibo_id,
      origem: 'Foto',
      itens
    });

    cancelarRecibo();
    await carregarDados();
  } catch (erro) {
    alert(erro.message);
    botao.disabled = false;
  }
}

function cancelarRecibo() {
  reciboAtual = null;

  const revisao = document.getElementById('revisaoRecibo');
  const formManual = document.getElementById('formManual');
  const preview = document.getElementById('fotoPreview');
  const hint = document.getElementById('fotoHint');
  const inputCamera = document.getElementById('fotoCameraInput');
  const inputGaleria = document.getElementById('fotoGaleriaInput');

  if (revisao) { revisao.style.display = 'none'; revisao.innerHTML = ''; }
  if (formManual) formManual.style.display = '';
  if (preview) { preview.style.display = 'none'; preview.src = ''; }
  if (hint) hint.textContent = 'Tira uma foto ou escolhe da galeria pra ler o recibo automaticamente.';
  if (inputCamera) inputCamera.value = '';
  if (inputGaleria) inputGaleria.value = '';
}

document.getElementById('fotoCameraInput')?.addEventListener('change', evento => {
  const arquivo = evento.target.files?.[0];
  if (arquivo) processarFotoRecibo(arquivo);
});

document.getElementById('fotoGaleriaInput')?.addEventListener('change', evento => {
  const arquivo = evento.target.files?.[0];
  if (arquivo) processarFotoRecibo(arquivo);
});

document.getElementById('btnEscanearDesktop')?.addEventListener('click', () => {
  document.getElementById('fotoGaleriaInput')?.click();
});
