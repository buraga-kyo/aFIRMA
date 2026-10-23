const quadro = document.querySelector('#chatwoot');
const origemChatwoot = new URL(quadro.src).origin;
const caminhosPermitidos = new Set(['/', '/assinatura', '/chat', '/chat/canais', '/chat/gestao', '/advai']);

window.addEventListener('message', evento => {
  if (evento.source !== quadro.contentWindow || evento.origin !== origemChatwoot) return;
  if (evento.data?.tipo !== 'afirma-navegar' || !caminhosPermitidos.has(evento.data.caminho)) return;
  window.location.assign(evento.data.caminho);
});
