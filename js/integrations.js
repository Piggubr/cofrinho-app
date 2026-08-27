const APPSSCRIPTURL =
  'https://script.google.com/macros/s/AKfycbwVY42eUQ40lIHG9B2Be9tOBA2eqWQBJ8mphxpg7sR84t0TLzuXrv-kJjQNWIiNxUag/exec';

const GOOGLECLIENTID =
  '831217793955-psvub19f0qhbeuphteig7bkmom80s0ik.apps.googleusercontent.com';

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
  if (!sessionToken) {
    throw new Error('Faça login para continuar.');
  }

  const resposta = await fetch(APPSSCRIPTURL, {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain;charset=utf-8'
    },
    body: JSON.stringify({
      ...dados,
      idtoken: googleIdToken || undefined,
      sessiontoken: sessionToken || undefined
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

    const alvo = document.querySelector('.info-row .info-item:nth-child(3)');

    if (!alvo) {
      return;
    }

    alvo.replaceChildren();

    const wise = document.createElement('img');
    wise.src = 'assets/icons/wise.svg';
    wise.alt = 'Wise';
    wise.style.width = '70px';
    wise.style.height = '18px';
    wise.style.objectFit = 'contain';

    const cotacao = document.createElement('strong');
    cotacao.textContent = `— R$ ${taxa.toFixed(2).replace('.', ',')}`;

    alvo.append(wise, cotacao);

    if (dados.desatualizada) {
      alvo.title = 'Última cotação de referência; não inclui taxas da Wise.';
    }

    cotacaoAtualizadaEm = Date.now();
  } catch (erro) {
    console.warn('Cotação temporariamente indisponível.', erro);
  }
}