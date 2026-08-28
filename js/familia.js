let cartinhaFotoSelecionada = null;

function formatarEuroFamiliar(valor) {
  return new Intl.NumberFormat('pt-PT', { style: 'currency', currency: 'EUR' }).format(Number(valor) || 0);
}

function renderPainelFamiliar() {
  const primeiroNome = String(nomeAtual || 'você').trim().split(/\s+/)[0];
  document.getElementById('saudacaoFamiliar').textContent = `Olá, ${primeiroNome}!`;
  document.getElementById('saldoFamiliar').textContent = formatarEuroFamiliar(cofrinhoResumo.saldo);
  const assinatura = document.getElementById('cartinhaAssinatura');
  if (assinatura && !assinatura.value) assinatura.value = apelidoAtual || nomeAtual;
  renderListaFamiliar('depositosFamiliar', meusDepositos, item => `${item.data || ''} · ${formatarEuroFamiliar(item.valor)}`);
  renderListaFamiliar('cartinhasEnviadas', minhasCartinhas, item => `${item.data || ''} · ${item.lida ? 'Lida' : 'Ainda não lida'}`);
}

function renderListaFamiliar(id, itens, textoItem) {
  const alvo = document.getElementById(id);
  if (!alvo) return;
  alvo.replaceChildren();
  if (!itens.length) {
    const vazio = document.createElement('p');
    vazio.className = 'empty';
    vazio.textContent = 'Nada por aqui ainda.';
    alvo.append(vazio);
    return;
  }
  itens.forEach(item => {
    const linha = document.createElement('div');
    linha.className = 'familiar-history-row';
    linha.textContent = textoItem(item);
    alvo.append(linha);
  });
}

function lerFotoCartinha(arquivo) {
  return new Promise((resolve, reject) => {
    const leitor = new FileReader();
    leitor.onload = () => {
      const resultado = String(leitor.result || '');
      resolve({ base64: resultado.split(',')[1] || '', mime: arquivo.type });
    };
    leitor.onerror = () => reject(new Error('Não foi possível ler a foto.'));
    leitor.readAsDataURL(arquivo);
  });
}

document.getElementById('cartinhaFoto')?.addEventListener('change', evento => {
  const arquivo = evento.target.files?.[0] || null;
  cartinhaFotoSelecionada = arquivo;
  const preview = document.getElementById('cartinhaPreview');
  if (!preview || !arquivo) return;
  preview.src = URL.createObjectURL(arquivo);
  preview.hidden = false;
});

document.getElementById('btnDepositoFamiliar')?.addEventListener('click', async () => {
  const botao = document.getElementById('btnDepositoFamiliar');
  const campo = document.getElementById('depositoFamiliarValor');
  const feedback = document.getElementById('depositoFamiliarFeedback');
  try {
    botao.disabled = true;
    feedback.textContent = 'Adicionando...';
    await chamarAppsScript({ action: 'addDeposit', valor: Number(campo.value) });
    campo.value = '';
    feedback.textContent = 'Dinheiro adicionado ao cofrinho.';
    await carregarDados();
  } catch (erro) {
    feedback.textContent = erro.message;
  } finally {
    botao.disabled = false;
  }
});

document.getElementById('btnEnviarCartinha')?.addEventListener('click', async () => {
  const botao = document.getElementById('btnEnviarCartinha');
  const feedback = document.getElementById('cartinhaFeedback');
  try {
    botao.disabled = true;
    feedback.textContent = 'Enviando...';
    if (!cartinhaFotoSelecionada) throw new Error('Escolha uma foto.');
    if (cartinhaFotoSelecionada.size > 5 * 1024 * 1024) throw new Error('A foto deve ter até 5 MB.');
    const foto = await lerFotoCartinha(cartinhaFotoSelecionada);
    await chamarAppsScript({
      action: 'saveCartinha',
      texto: document.getElementById('cartinhaTexto').value,
      assinatura: document.getElementById('cartinhaAssinatura').value,
      template: 'classico',
      image_base64: foto.base64,
      mime_type: foto.mime
    });
    document.getElementById('cartinhaTexto').value = '';
    document.getElementById('cartinhaFoto').value = '';
    document.getElementById('cartinhaPreview').hidden = true;
    cartinhaFotoSelecionada = null;
    feedback.textContent = 'Cartinha enviada para a Beatriz.';
    await carregarDados();
  } catch (erro) {
    feedback.textContent = erro.message;
  } finally {
    botao.disabled = false;
  }
});
