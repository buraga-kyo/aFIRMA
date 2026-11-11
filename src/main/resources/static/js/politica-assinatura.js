window.politicaAssinatura = (() => {
  const pronta = fetch('/api/assinaturas/politica', { cache: 'no-store' }).then(async resposta => {
    if (!resposta.ok) throw new Error('Não foi possível carregar as regras da assinatura.');
    const politica = await resposta.json();
    for (const seletor of document.querySelectorAll('[data-tipo-documento]')) {
      seletor.replaceChildren();
      for (const tipo of politica.tiposDocumento) {
        const opcao = document.createElement('option');
        opcao.value = tipo.codigo;
        opcao.textContent = tipo.nome;
        seletor.append(opcao);
      }
    }
    const modoCarteira = document.getElementById('modo-blockchain');
    if (modoCarteira) modoCarteira.hidden = !politica.carteiraExperimental;
    if (!politica.fotografiaPermitida) document.querySelector('#tipo-campo option[value="SELFIE"]')?.remove();
    for (const elemento of document.querySelectorAll('[data-declaracao-formalidades]')) elemento.textContent = politica.declaracaoFormalidades;
    for (const elemento of document.querySelectorAll('[data-declaracao-poderes]')) elemento.textContent = politica.declaracaoPoderes;
    for (const elemento of document.querySelectorAll('[data-aviso-privacidade]')) {
      if (!elemento.dataset.avisoPreservado) elemento.textContent = politica.avisoPrivacidade;
    }
    return politica;
  });
  pronta.catch(() => {
    for (const elemento of document.querySelectorAll('[data-aviso-privacidade]')) elemento.textContent = 'As regras de assinatura estão indisponíveis. Aguarde antes de continuar.';
  });

  async function formalidades() {
    const politica = await pronta;
    if (!document.getElementById('formalidades-documento').checked) throw new Error('Confirme a revisão das formalidades do documento.');
    return { tipoDocumento: document.getElementById('tipo-documento').value, declaracaoFormalidades: politica.declaracaoFormalidades };
  }
  async function poderes() {
    const politica = await pronta;
    if (!document.getElementById('poderes-certificado').checked) throw new Error('Confirme a autorização para usar o certificado.');
    return politica.declaracaoPoderes;
  }
  async function solicitarRevisao(comCertificado = false) {
    const politica = await pronta;
    const janela = document.createElement('dialog');
    const formulario = document.createElement('form');
    formulario.method = 'dialog';
    const titulo = document.createElement('h2');
    titulo.textContent = comCertificado ? 'Autorização para usar o certificado' : 'Formalidades do documento';
    formulario.append(titulo);
    const tipo = document.createElement('select');
    if (!comCertificado) {
      for (const item of politica.tiposDocumento) {
        const opcao = document.createElement('option');
        opcao.value = item.codigo; opcao.textContent = item.nome; tipo.append(opcao);
      }
      formulario.append(tipo);
    }
    const rotulo = document.createElement('label');
    const aceite = document.createElement('input');
    aceite.type = 'checkbox'; aceite.required = true;
    rotulo.append(aceite, document.createTextNode(comCertificado ? politica.declaracaoPoderes : politica.declaracaoFormalidades));
    formulario.append(rotulo);
    const continuar = document.createElement('button');
    continuar.textContent = 'Continuar'; continuar.value = 'continuar';
    const cancelar = document.createElement('button');
    cancelar.textContent = 'Cancelar'; cancelar.type = 'button';
    cancelar.addEventListener('click', () => janela.close());
    formulario.append(continuar, cancelar); janela.append(formulario); document.body.append(janela);
    janela.showModal();
    return new Promise((resolver, rejeitar) => janela.addEventListener('close', () => {
      const aceito = janela.returnValue === 'continuar' && aceite.checked;
      const escolha = tipo.value; janela.remove();
      if (!aceito) return rejeitar(new Error('Operação cancelada.'));
      resolver(comCertificado ? politica.declaracaoPoderes
        : { tipoDocumento: escolha, declaracaoFormalidades: politica.declaracaoFormalidades });
    }, { once: true }));
  }
  return { pronta, formalidades, poderes, solicitarRevisao };
})();
