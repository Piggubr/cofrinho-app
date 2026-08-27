let lugares = [];

function renderLugares() {
  const lista = document.getElementById('lugaresLista');
  if (!lista) return;

  if (!lugares.length) {
    lista.innerHTML = '<p class="empty">Nenhum lugar registrado ainda.</p>';
    return;
  }

  lista.innerHTML = lugares.map(lugar => {
    const foto = lugar.url
      ? `<img src="${esc(lugar.url)}" alt="Foto de ${esc(lugar.nome)}" loading="lazy" decoding="async">`
      : (lugar.temFoto ? 'Carregando foto...' : 'Sem foto');

    const estrelas = Array.from({ length: Math.max(1, Math.min(5, Number(lugar.nota) || 0)) }, () =>
      `<img src="assets/icons/estrela.svg" alt="">`
    ).join('');

    return `
      <div class="place-item">
        <div class="place-photo">${foto}</div>
        <div class="place-info">
          <div class="place-item-top">
            <div>
              <p class="place-name">${esc(lugar.nome)}</p>
              <p class="place-meta">${esc(lugar.categoria)} ${lugar.localizacao ? '• ' + esc(lugar.localizacao) : ''} ${lugar.data ? '• ' + esc(lugar.data) : ''}</p>
              ${Number(lugar.valor) > 0 ? `<span class="valor-privado">${fmt(Number(lugar.valor))}</span>` : ''}
            </div>
            <span class="place-rating" aria-label="Nota ${lugar.nota}">${estrelas}</span>
          </div>
          ${lugar.comentario ? `<p class="place-comment">${esc(lugar.comentario)}</p>` : ''}
          <div class="place-badges">
            ${(lugar.marcacoes || []).map(m => `<span class="place-badge">${esc(m)}</span>`).join('')}
          </div>
        </div>
        <div style="display:flex;gap:8px;margin-top:8px;">
          <button class="icon-btn" data-edit-place="${lugar.id}">Editar</button>
          <button class="icon-btn" data-del-place="${lugar.id}">Excluir</button>
        </div>
      </div>
    `;
  }).join('');

  lista.querySelectorAll('[data-del-place]').forEach(botao => {
    botao.addEventListener('click', async () => {
      if (!confirm('Excluir este lugar?')) return;
      try {
        await chamarAppsScript({ action: 'deletePlace', id: botao.dataset.delPlace });
        await carregarDados();
      } catch (erro) {
        alert(erro.message);
      }
    });
  });

  lista.querySelectorAll('[data-edit-place]').forEach(botao => {
    botao.addEventListener('click', () => {
      editarLugar(botao.dataset.editPlace);
    });
  });
}

let lugarFotoSelecionada = null;

function editarLugar(id) {
  const lugar = lugares.find(l => String(l.id) === String(id));
  if (!lugar) return;

  document.getElementById('lugarNome').value = lugar.nome || '';
  document.getElementById('lugarCategoria').value = lugar.categoria || 'Restaurante';
  document.getElementById('lugarNota').value = lugar.nota || 5;
  document.getElementById('lugarLocalizacao').value = lugar.localizacao || '';
  document.getElementById('lugarData').value = lugar.data || '';
  document.getElementById('lugarValor').value = lugar.valor || '';
  document.getElementById('lugarComentario').value = lugar.comentario || '';

  lugarFotoSelecionada = lugar.url || null;
  const preview = document.getElementById('lugarFotoPreview');
  if (preview) {
    preview.style.display = lugarFotoSelecionada ? 'block' : 'none';
    if (lugarFotoSelecionada) preview.src = lugarFotoSelecionada;
  }

  document.getElementById('btnSalvarLugar').onclick = salvarLugarEdicao;
}

function salvarLugarEdicao() {
  // Implementação simplificada; adaptar conforme sua lógica original
  alert('Salvar edição de lugar (implementar conforme original).');
}

async function carregarFotosLugares() {
  const pendentes = lugares.filter(lugar => lugar.temFoto && !lugar.url);
  await Promise.all(pendentes.map(async lugar => {
    try {
      const resposta = await chamarAppsScript({ action: 'getPlacePhoto', id: lugar.id });
      lugar.url = resposta.url || resposta.image_base64 || '';
    } catch (erro) {
      console.warn('Não foi possível carregar a foto do lugar.', erro);
    }
  }));
  renderLugares();
}
