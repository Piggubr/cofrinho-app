function abrirModal(titulo, conteudoHTML) {
  document.getElementById('modalTitulo').textContent = titulo;
  document.getElementById('modalConteudo').innerHTML = conteudoHTML;
  document.getElementById('modalOverlay').classList.add('show');
}

function fecharModal() {
  document.getElementById('modalOverlay').classList.remove('show');
}

document.getElementById('modalFechar')?.addEventListener('click', fecharModal);

document.getElementById('modalOverlay')?.addEventListener('click', e => {
  if (e.target.id === 'modalOverlay') {
    fecharModal();
  }
});