function renderCartinhas() {
  const alvo = document.getElementById('cartinhasRecebidas');
  const badge = document.getElementById('cartinhasBadge');
  if (!alvo) return;
  alvo.replaceChildren();
  const naoLidas = minhasCartinhas.filter(item => !item.lida).length;
  if (badge) {
    badge.hidden = naoLidas === 0;
    badge.textContent = String(naoLidas);
  }
  if (!minhasCartinhas.length) {
    const vazio = document.createElement('p');
    vazio.className = 'empty';
    vazio.textContent = 'Nenhuma cartinha recebida ainda.';
    alvo.append(vazio);
    return;
  }
  minhasCartinhas.forEach(cartinha => {
    const botao = document.createElement('button');
    botao.type = 'button';
    botao.className = `cartinha-card${cartinha.lida ? '' : ' nao-lida'}`;
    const titulo = document.createElement('strong');
    titulo.textContent = cartinha.assinatura || cartinha.remetente;
    const data = document.createElement('span');
    data.textContent = cartinha.data || '';
    botao.append(titulo, data);
    botao.addEventListener('click', () => abrirCartinha(cartinha));
    alvo.append(botao);
  });
}

async function abrirCartinha(cartinha) {
  const overlay = document.getElementById('modalOverlay');
  const titulo = document.getElementById('modalTitulo');
  const conteudo = document.getElementById('modalConteudo');
  if (!overlay || !conteudo) return;
  titulo.textContent = 'Cartinha';
  conteudo.replaceChildren();
  const postal = document.createElement('div');
  postal.className = 'postal-view';
  const frente = document.createElement('div');
  frente.className = 'postal-face';
  frente.textContent = 'Carregando foto...';
  const verso = document.createElement('div');
  verso.className = 'postal-face postal-back';
  verso.hidden = true;
  const texto = document.createElement('p');
  texto.textContent = cartinha.texto;
  const assinatura = document.createElement('strong');
  assinatura.textContent = cartinha.assinatura || cartinha.remetente;
  const data = document.createElement('small');
  data.textContent = cartinha.data || '';
  verso.append(texto, assinatura, data);
  const virar = document.createElement('button');
  virar.type = 'button';
  virar.className = 'primary';
  virar.textContent = 'Virar';
  virar.addEventListener('click', () => {
    const mostrarVerso = verso.hidden;
    frente.hidden = mostrarVerso;
    verso.hidden = !mostrarVerso;
    virar.textContent = mostrarVerso ? 'Ver frente' : 'Virar';
  });
  postal.append(frente, verso, virar);
  conteudo.append(postal);
  overlay.classList.add('show');
  try {
    const foto = await chamarAppsScript({ action: 'getCartinhaFoto', id: cartinha.id });
    frente.replaceChildren();
    if (foto.url) {
      const imagem = document.createElement('img');
      imagem.src = foto.url;
      imagem.alt = 'Foto da cartinha';
      frente.append(imagem);
    } else frente.textContent = 'Cartinha sem foto.';
    if (!cartinha.lida && perfilAtual === 'BEATRIZ') {
      await chamarAppsScript({ action: 'markCartinhaLida', id: cartinha.id });
      cartinha.lida = true;
      renderCartinhas();
    }
  } catch (erro) {
    frente.textContent = erro.message;
  }
}
