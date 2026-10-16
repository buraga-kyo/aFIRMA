const formulario = document.getElementById('formulario-assinatura');
const entradaPdf = document.getElementById('pdf');
const zonaPdf = document.getElementById('zona-pdf');
const nomePdf = document.getElementById('nome-pdf');
const entradaCertificado = document.getElementById('certificado');
const removerPdf = document.getElementById('remover-pdf');
const removerCertificado = document.getElementById('remover-certificado');
const estadoPdf = document.getElementById('estado-pdf');
const estadoCertificado = document.getElementById('estado-certificado');
const botaoAssinar = document.getElementById('botao-assinar');
const resultado = document.getElementById('resultado');
const baixar = document.getElementById('baixar');
const caixaErro = document.getElementById('erro');
let enderecoArquivo = null;

function mostrarNomePdf() {
  const arquivo = entradaPdf.files[0];
  nomePdf.textContent = arquivo?.name || 'Clique para escolher seu PDF';
  removerPdf.hidden = !arquivo;
  estadoPdf.textContent = arquivo ? 'PDF selecionado.' : 'Nenhum PDF selecionado.';
}

function limparResultado() {
  resultado.hidden = true;
  baixar.removeAttribute('href');
  if (enderecoArquivo) {
    URL.revokeObjectURL(enderecoArquivo);
    enderecoArquivo = null;
  }
}

entradaPdf.addEventListener('change', () => {
  mostrarNomePdf();
  limparResultado();
});
entradaCertificado.addEventListener('change', () => {
  const arquivo = entradaCertificado.files[0];
  removerCertificado.hidden = !arquivo;
  estadoCertificado.textContent = arquivo ? `Certificado selecionado: ${arquivo.name}.` : 'Nenhum certificado selecionado.';
  limparResultado();
});

removerPdf.addEventListener('click', () => {
  entradaPdf.value = '';
  mostrarNomePdf();
  estadoPdf.textContent = 'PDF removido da seleção. Escolha outro arquivo.';
  limparResultado();
});
removerCertificado.addEventListener('click', () => {
  entradaCertificado.value = '';
  removerCertificado.hidden = true;
  estadoCertificado.textContent = 'Certificado removido da seleção. Escolha outro arquivo.';
  limparResultado();
});

for (const botao of document.querySelectorAll('.botao-olho')) {
  botao.addEventListener('click', () => {
    const entrada = document.getElementById(botao.dataset.alvo);
    const visivel = entrada.type === 'password';
    entrada.type = visivel ? 'text' : 'password';
    botao.setAttribute('aria-pressed', String(visivel));
    botao.setAttribute('aria-label', `${visivel ? 'Ocultar' : 'Mostrar'} ${botao.dataset.alvo === 'senha' ? 'senha do certificado' : botao.dataset.alvo === 'token' ? 'token JWT' : 'chave da API'}`);
  });
}

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
    const arquivos = new DataTransfer();
    arquivos.items.add(arquivo);
    entradaPdf.files = arquivos.files;
    mostrarNomePdf();
    limparResultado();
  } else {
    caixaErro.textContent = 'Escolha um arquivo PDF.';
    caixaErro.hidden = false;
  }
});

formulario.addEventListener('submit', async ocorrencia => {
  ocorrencia.preventDefault();
  caixaErro.hidden = true;
  limparResultado();

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
        415: 'O arquivo enviado não é um PDF válido.',
        429: 'Muitas tentativas. Aguarde um minuto.',
        500: 'Falha interna ao assinar. Consulte os logs do servidor.',
        503: 'Serviço indisponível. Tente novamente em instantes.'
      };
      let mensagem = mensagens[resposta.status] || 'Não foi possível assinar o documento. Tente novamente.';
      if ([400, 413, 415, 500, 503].includes(resposta.status) && resposta.headers.get('content-type')?.includes('application/json')) {
        const detalhe = await resposta.json().catch(() => null);
        if (typeof detalhe?.mensagem === 'string' && detalhe.mensagem.trim()) mensagem = detalhe.mensagem;
      }
      throw new Error(mensagem);
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
