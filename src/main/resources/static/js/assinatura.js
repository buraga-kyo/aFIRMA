const formulario = document.getElementById('formulario-assinatura');
const entradaPdf = document.getElementById('pdf');
const zonaPdf = document.getElementById('zona-pdf');
const nomePdf = document.getElementById('nome-pdf');
const botaoAssinar = document.getElementById('botao-assinar');
const resultado = document.getElementById('resultado');
const baixar = document.getElementById('baixar');
const caixaErro = document.getElementById('erro');
let enderecoArquivo = null;

function mostrarNomePdf() {
  nomePdf.textContent = entradaPdf.files[0]?.name || 'Clique para escolher seu PDF';
}

entradaPdf.addEventListener('change', mostrarNomePdf);

for (const evento of ['dragenter', 'dragover']) {
  zonaPdf.addEventListener(evento, ocorrencia => {
    ocorrencia.preventDefault();
    zonaPdf.classList.add('arrastando');
  });
}

for (const evento of ['dragleave', 'drop']) {
  zonaPdf.addEventListener(evento, ocorrencia => {
    ocorrencia.preventDefault();
    zonaPdf.classList.remove('arrastando');
  });
}

zonaPdf.addEventListener('drop', ocorrencia => {
  const arquivo = ocorrencia.dataTransfer.files[0];
  if (arquivo?.type === 'application/pdf' || arquivo?.name.toLowerCase().endsWith('.pdf')) {
    entradaPdf.files = ocorrencia.dataTransfer.files;
    mostrarNomePdf();
  } else {
    caixaErro.textContent = 'Escolha um arquivo PDF.';
    caixaErro.hidden = false;
  }
});

formulario.addEventListener('submit', async ocorrencia => {
  ocorrencia.preventDefault();
  caixaErro.hidden = true;
  resultado.hidden = true;
  if (enderecoArquivo) {
    URL.revokeObjectURL(enderecoArquivo);
    enderecoArquivo = null;
  }

  const arquivo = entradaPdf.files[0];
  if (!arquivo || arquivo.size > 20 * 1024 * 1024 ||
      !(arquivo.type === 'application/pdf' || arquivo.name.toLowerCase().endsWith('.pdf'))) {
    caixaErro.textContent = 'Escolha um PDF de até 20 MB.';
    caixaErro.hidden = false;
    return;
  }

  botaoAssinar.disabled = true;
  botaoAssinar.setAttribute('aria-busy', 'true');
  botaoAssinar.querySelector('span').textContent = 'ASSINANDO DOCUMENTO...';

  try {
    const dados = new FormData(formulario);
    const chave = document.getElementById('chave-api').value.trim();
    const token = document.getElementById('token').value.trim();
    const resposta = await fetch(formulario.action, {
      method: 'POST',
      headers: { 'X-API-Key': chave, 'Authorization': `Bearer ${token}` },
      body: dados,
      cache: 'no-store'
    });

    if (!resposta.ok) {
      const mensagens = {
        400: 'Confira PDF, certificado e senha. Tente novamente.',
        401: 'Chave da API ou token inválido.',
        403: 'Token sem permissão para assinar PDF.',
        413: 'Arquivo acima do limite permitido.',
        429: 'Muitas tentativas. Aguarde um minuto.',
        503: 'Serviço indisponível. Tente novamente em instantes.'
      };
      throw new Error(mensagens[resposta.status] || 'Não foi possível assinar o documento. Tente novamente.');
    }

    const arquivoAssinado = await resposta.blob();
    if (!arquivoAssinado.size) throw new Error('O documento assinado veio vazio. Tente novamente.');
    enderecoArquivo = URL.createObjectURL(arquivoAssinado);
    baixar.href = enderecoArquivo;
    resultado.hidden = false;
    resultado.scrollIntoView({ block: 'nearest' });
  } catch (erro) {
    caixaErro.textContent = erro instanceof TypeError ? 'Falha de conexão. Tente novamente.' : erro.message;
    caixaErro.hidden = false;
  } finally {
    botaoAssinar.disabled = false;
    botaoAssinar.removeAttribute('aria-busy');
    botaoAssinar.querySelector('span').textContent = 'ASSINAR DOCUMENTO';
  }
});

window.addEventListener('pagehide', () => {
  if (enderecoArquivo) URL.revokeObjectURL(enderecoArquivo);
});
