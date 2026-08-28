async function carregarFotosDoMes() {
  const mesKey = mesKeyDe(viewDate);
  const fotosDoMes = fotos.filter(foto => (foto.mesKey || foto.mes) === mesKey);
  await Promise.all(fotosDoMes.filter(foto => !foto.url).map(async foto => {
    try {
      const resposta = await chamarAppsScript({ action: 'getFeedPhoto', id: foto.id });
      foto.url = resposta.url || resposta.image_base64 || '';
    } catch (erro) {
      console.warn('Falha ao carregar foto:', erro);
    }
  }));
  renderFeed();
}

function renderFeed() {
  const grid = document.getElementById('feedGrid');
  if (!grid) return;

  if (!fotos.length) {
    grid.innerHTML = '';
    return;
  }

  grid.innerHTML = fotos.map(f => {
    return `
      <div class="feed-item" data-id="${f.id}">
        <button type="button" class="feed-thumb">
          <img src="${esc(f.url)}" alt="${esc(f.legenda || '')}">
          ${f.legenda ? `<div class="feed-legenda">${esc(f.legenda)}</div>` : ''}
        </button>
        <button type="button" class="feed-remove" aria-label="Excluir foto">×</button>
      </div>
    `;
  }).join('') + `
    <button class="feed-item feed-add" id="btnAddFoto">
      +
    </button>
  `;

  document.getElementById('btnAddFoto')?.addEventListener('click', () => {
    document.getElementById('feedInput').click();
  });

  const excluirFoto = id => {
    if (!confirm('Excluir essa foto?')) return;
    chamarAppsScript({ action: 'deleteFeed', id })
      .then(() => {
        fotos = fotos.filter(f => f.id !== id);
        renderFeed();
      })
      .catch(erro => alert(erro.message));
  };

  grid.querySelectorAll('.feed-item:not(.feed-add)').forEach(item => {
    const id = Number(item.dataset.id);

    item.querySelector('.feed-remove')?.addEventListener('click', evento => {
      evento.stopPropagation();
      excluirFoto(id);
    });

    item.querySelector('.feed-thumb')?.addEventListener('click', () => {
      const foto = fotos.find(f => f.id === id);
      if (!foto) return;

      abrirModal('Legenda da foto', `
        <input id="legendaInput" type="text" value="${esc(foto.legenda || '')}" style="width:100%;margin-bottom:12px;">
        <button class="primary" id="btnSalvarLegenda">Salvar</button>
        <button id="btnExcluirFoto" style="margin-top:8px;border:1px solid var(--border);border-radius:10px;width:100%;padding:8px;">Excluir</button>
      `);

      document.getElementById('btnSalvarLegenda').onclick = () => {
        const legenda = document.getElementById('legendaInput').value.trim();
        chamarAppsScript({ action: 'updateFeed', id: foto.id, legenda })
          .then(() => {
            foto.legenda = legenda;
            fecharModal();
            renderFeed();
          })
          .catch(erro => alert(erro.message));
      };

      document.getElementById('btnExcluirFoto').onclick = () => {
        fecharModal();
        excluirFoto(foto.id);
      };
    });
  });
}

document.getElementById('feedInput')?.addEventListener('change', e => {
  const arquivos = e.target.files;
  if (!arquivos || !arquivos.length) return;

  Array.from(arquivos).forEach(file => {
    const reader = new FileReader();
    reader.onload = () => {
      const url = reader.result;
      fotos.push({
        id: nextFotoId++,
        url,
        legenda: '',
        mes: mesKeyDe(viewDate)
      });
      renderFeed();
      salvarEstado();
    };
    reader.readAsDataURL(file);
  });

  e.target.value = '';
});
