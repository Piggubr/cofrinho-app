const APPSSCRIPTURL =
  'https://script.google.com/macros/s/AKfycbxmyeqNTBPiuF597BFj7bHhZKwDehaC6bLVKoHGbPmLtA74eeznWxZDMQXfPgeMrtVx/exec';

const GOOGLECLIENTID =
  '532290439779-olhgd7m1hssj4o30gjs3ngrcs7ga48aa.apps.googleusercontent.com';

let googleIdToken = null;
let sessionToken = null;
let carregamentoDadosEmCurso = null;
let cotacaoAtualizadaEm = 0;

try {
  sessionToken =
    localStorage.getItem('cofrinho_sessiontoken') ||
    sessionStorage.getItem('cofrinho_sessiontoken');
} catch (erro) {
  console.warn('Não foi possível ler a sessão salva.', erro);
}

async function chamarAppsScript(dados) {
  if (window.PIGGU_DEMO_MODE) {
    throw new Error('O modo sem login não acessa nem altera dados privados.');
  }

  if (!googleIdToken && !sessionToken) {
    throw new Error('Faça login com Google.');
  }

  const resposta = await fetch(APPSSCRIPTURL, {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain;charset=utf-8'
    },
    body: JSON.stringify({
      ...dados,
      id_token: googleIdToken || undefined,
      session_token: sessionToken || undefined
    })
  });

  let resultado;

  try {
    resultado = await resposta.json();
  } catch (erro) {
    throw new Error('O servidor retornou uma resposta inválida.');
  }

  if (!resposta.ok) {
    throw new Error(resultado?.erro || 'Não foi possível concluir a solicitação.');
  }

  if (!resultado?.success) {
    throw new Error(resultado?.erro || 'Não foi possível concluir a solicitação.');
  }

  return resultado;
}

async function atualizarCotacaoCambio() {
  const intervaloMinimo = 30 * 60 * 1000;

  if (Date.now() - cotacaoAtualizadaEm < intervaloMinimo) {
    return;
  }

  try {
    const dados = await chamarAppsScript({
      action: 'getExchangeRate'
    });

    const taxa = Number(dados.taxa);

    if (!Number.isFinite(taxa) || taxa <= 0) {
      return;
    }

    const alvo = document.querySelector('.info-row .info-item');

    if (!alvo) {
      return;
    }

    alvo.replaceChildren();

    const wise = document.createElement('img');
    wise.src = 'assets/logos/wise.svg';
    wise.alt = 'Wise';
    wise.style.width = '70px';
    wise.style.height = '18px';
    wise.style.objectFit = 'contain';

    const cotacao = document.createElement('strong');
    cotacao.textContent = ` - R$ ${taxa.toFixed(2).replace('.', ',')}`;

    alvo.append(wise, cotacao);

    if (dados.desatualizada) {
      alvo.title = 'Última cotação de referência; não inclui taxas da Wise.';
    }

    cotacaoAtualizadaEm = Date.now();
  } catch (erro) {
    console.warn('Cotação temporariamente indisponível.', erro);
  }
}
