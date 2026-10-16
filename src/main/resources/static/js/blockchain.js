const botaoModoCertificado = document.getElementById('modo-certificado');
const botaoModoBlockchain = document.getElementById('modo-blockchain');
const formularioCertificado = document.getElementById('formulario-assinatura');
const areaBlockchain = document.getElementById('area-blockchain');
const entradaPdfBlockchain = document.getElementById('pdf-blockchain');
const zonaPdfBlockchain = document.getElementById('zona-pdf-blockchain');
const nomePdfBlockchain = document.getElementById('nome-pdf-blockchain');
const removerPdfBlockchain = document.getElementById('remover-pdf-blockchain');
const estadoPdfBlockchain = document.getElementById('estado-pdf-blockchain');
const conectarCarteira = document.getElementById('conectar-carteira');
const estadoCarteira = document.getElementById('estado-carteira');
const assinarBlockchain = document.getElementById('assinar-blockchain');
const andamentoBlockchain = document.getElementById('andamento-blockchain');
const erroBlockchain = document.getElementById('erro-blockchain');
const resultadoBlockchain = document.getElementById('resultado-blockchain');
const baixarBlockchain = document.getElementById('baixar-blockchain');
const resultadoCertificado = document.getElementById('resultado');
const erroCertificado = document.getElementById('erro');
const chavePendente = 'afirmaAssinaturaPendente';
const destinoRegistro = '0x000000000000000000000000000000000000dEaD';
let provedorCarteira = null;
let carteiraConectada = null;
let enderecoPdfBlockchain = null;
let operacaoInterrompida = false;

function escolherModo(blockchain) {
  botaoModoCertificado.setAttribute('aria-pressed', String(!blockchain));
  botaoModoBlockchain.setAttribute('aria-pressed', String(blockchain));
  formularioCertificado.hidden = blockchain;
  areaBlockchain.hidden = !blockchain;
  resultadoCertificado.hidden = true;
  erroCertificado.hidden = true;
}

botaoModoCertificado.addEventListener('click', () => escolherModo(false));
botaoModoBlockchain.addEventListener('click', () => escolherModo(true));

function limparResultadoBlockchain() {
  resultadoBlockchain.hidden = true;
  baixarBlockchain.removeAttribute('href');
  if (enderecoPdfBlockchain) URL.revokeObjectURL(enderecoPdfBlockchain);
  enderecoPdfBlockchain = null;
}

function atualizarPdfBlockchain() {
  const arquivo = entradaPdfBlockchain.files[0];
  nomePdfBlockchain.textContent = arquivo?.name || 'Clique para escolher seu PDF';
  removerPdfBlockchain.hidden = !arquivo;
  estadoPdfBlockchain.textContent = arquivo ? 'PDF selecionado.' : 'Nenhum PDF selecionado.';
  limparResultadoBlockchain();
}

entradaPdfBlockchain.addEventListener('change', atualizarPdfBlockchain);
removerPdfBlockchain.addEventListener('click', () => {
  entradaPdfBlockchain.value = '';
  atualizarPdfBlockchain();
  estadoPdfBlockchain.textContent = 'PDF removido da seleção. Escolha outro arquivo.';
});

for (const evento of ['dragenter', 'dragover']) {
  zonaPdfBlockchain.addEventListener(evento, ocorrencia => {
    ocorrencia.preventDefault();
    zonaPdfBlockchain.classList.add('arrastando');
  });
}
for (const evento of ['dragleave', 'drop']) {
  zonaPdfBlockchain.addEventListener(evento, ocorrencia => {
    ocorrencia.preventDefault();
    zonaPdfBlockchain.classList.remove('arrastando');
  });
}
zonaPdfBlockchain.addEventListener('drop', ocorrencia => {
  const arquivo = ocorrencia.dataTransfer.files[0];
  if (!arquivo || !(arquivo.type === 'application/pdf' || arquivo.name.toLowerCase().endsWith('.pdf'))) {
    mostrarErroBlockchain('Escolha um arquivo PDF.');
    return;
  }
  const arquivos = new DataTransfer();
  arquivos.items.add(arquivo);
  entradaPdfBlockchain.files = arquivos.files;
  atualizarPdfBlockchain();
});

function mostrarErroBlockchain(mensagem) {
  erroBlockchain.textContent = mensagem;
  erroBlockchain.hidden = false;
  andamentoBlockchain.textContent = '';
}

function obterProvedor() {
  const disponiveis = window.ethereum?.providers || [window.ethereum];
  const encontrado = disponiveis.find(provedor => provedor?.isMetaMask);
  if (!encontrado) throw new Error('MetaMask não encontrada neste navegador.');
  return encontrado;
}

async function garantirSepolia() {
  let rede = await provedorCarteira.request({ method: 'eth_chainId' });
  if (rede === '0xaa36a7') return;
  try {
    await provedorCarteira.request({ method: 'wallet_switchEthereumChain', params: [{ chainId: '0xaa36a7' }] });
  } catch (erro) {
    if (erro.code !== 4902) throw erro;
    await provedorCarteira.request({
      method: 'wallet_addEthereumChain',
      params: [{ chainId: '0xaa36a7', chainName: 'Ethereum Sepolia',
        nativeCurrency: { name: 'Sepolia ETH', symbol: 'ETH', decimals: 18 },
        rpcUrls: ['https://ethereum-sepolia-rpc.publicnode.com'],
        blockExplorerUrls: ['https://sepolia.etherscan.io'] }]
    });
  }
  rede = await provedorCarteira.request({ method: 'eth_chainId' });
  if (rede !== '0xaa36a7') throw new Error('Selecione a rede Ethereum Sepolia na MetaMask.');
}

async function conectarMetaMask() {
  provedorCarteira = obterProvedor();
  const contas = await provedorCarteira.request({ method: 'eth_requestAccounts' });
  if (!contas?.[0]) throw new Error('Nenhuma conta foi autorizada na MetaMask.');
  await garantirSepolia();
  carteiraConectada = contas[0];
  estadoCarteira.textContent = `Conectada: ${carteiraConectada}`;
  return carteiraConectada;
}

conectarCarteira.addEventListener('click', async () => {
  erroBlockchain.hidden = true;
  try {
    await conectarMetaMask();
  } catch (erro) {
    mostrarErroBlockchain(erro.code === 4001 ? 'Conexão recusada na MetaMask.' : erro.message);
  }
});

function bytesHexadecimal(texto) {
  const pares = texto.slice(2).match(/.{2}/g) || [];
  return Uint8Array.from(pares, par => Number.parseInt(par, 16));
}

async function resumoAssinatura(assinatura) {
  const bytes = await crypto.subtle.digest('SHA-256', bytesHexadecimal(assinatura));
  return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('');
}

async function mensagemResposta(resposta) {
  const dados = await resposta.json().catch(() => null);
  return dados?.mensagem || dados?.detail || `Operação recusada pelo servidor (${resposta.status}).`;
}

function esperar(tempo) {
  return new Promise(concluir => setTimeout(concluir, tempo));
}

async function aguardarFinalizacao(pendente) {
  const inicio = Date.now();
  while (!operacaoInterrompida && Date.now() - inicio < 3600000) {
    const resposta = await fetch('/api/blockchain/concluir', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(pendente), cache: 'no-store'
    });
    if (resposta.status === 202) {
      andamentoBlockchain.textContent = 'Transação enviada. Aguardando finalização na Sepolia. Isso pode levar vários minutos.';
      await esperar(10000);
      continue;
    }
    if (!resposta.ok) {
      if (resposta.status === 404 || resposta.status === 410) sessionStorage.removeItem(chavePendente);
      throw new Error(await mensagemResposta(resposta));
    }
    const pdf = await resposta.blob();
    if (!pdf.size) throw new Error('O PDF assinado veio vazio.');
    limparResultadoBlockchain();
    enderecoPdfBlockchain = URL.createObjectURL(pdf);
    baixarBlockchain.href = enderecoPdfBlockchain;
    resultadoBlockchain.hidden = false;
    andamentoBlockchain.textContent = 'Transação finalizada. PDF pronto para baixar.';
    sessionStorage.removeItem(chavePendente);
    return;
  }
  if (operacaoInterrompida) throw new Error('Conta ou rede alterada. Conecte novamente para continuar.');
  throw new Error('Tempo de espera esgotado. Envie o PDF novamente.');
}

assinarBlockchain.addEventListener('click', async () => {
  erroBlockchain.hidden = true;
  limparResultadoBlockchain();
  const arquivo = entradaPdfBlockchain.files[0];
  if (!arquivo || arquivo.size > 20 * 1024 * 1024) {
    mostrarErroBlockchain('Escolha um PDF de até 20 MB.');
    return;
  }
  assinarBlockchain.disabled = true;
  operacaoInterrompida = false;
  try {
    const carteira = await conectarMetaMask();
    andamentoBlockchain.textContent = 'Preparando PDF...';
    const dados = new FormData();
    dados.append('pdf', arquivo);
    dados.append('carteira', carteira);
    const resposta = await fetch('/api/blockchain/preparar', { method: 'POST', body: dados, cache: 'no-store' });
    if (!resposta.ok) throw new Error(await mensagemResposta(resposta));
    const preparo = await resposta.json();
    const mensagem = {
      domain: { name: 'aFIRMA', version: '1', chainId: 11155111 },
      types: {
        EIP712Domain: [
          { name: 'name', type: 'string' }, { name: 'version', type: 'string' },
          { name: 'chainId', type: 'uint256' }
        ],
        AssinaturaPdf: [
          { name: 'resumo', type: 'bytes32' }, { name: 'carteira', type: 'address' },
          { name: 'identificador', type: 'bytes32' }, { name: 'prazo', type: 'uint256' }
        ]
      },
      primaryType: 'AssinaturaPdf',
      message: { resumo: preparo.resumoPdf, carteira, identificador: preparo.identificadorAssinado,
        prazo: preparo.prazo }
    };
    andamentoBlockchain.textContent = 'Confirme a assinatura do resumo do PDF na MetaMask.';
    const assinatura = await provedorCarteira.request({
      method: 'eth_signTypedData_v4', params: [carteira, JSON.stringify(mensagem)]
    });
    if (operacaoInterrompida) throw new Error('Conta ou rede alterada durante a assinatura.');
    await garantirSepolia();
    const contasAtuais = await provedorCarteira.request({ method: 'eth_accounts' });
    if (contasAtuais?.[0]?.toLowerCase() !== carteira.toLowerCase()) {
      throw new Error('Conta alterada durante a assinatura.');
    }
    const dadosTransacao = '0x414649524d4131' + preparo.resumoPdf.slice(2) + await resumoAssinatura(assinatura);
    andamentoBlockchain.textContent = 'Confirme a transação de valor zero na Sepolia.';
    const transacao = await provedorCarteira.request({
      method: 'eth_sendTransaction', params: [{ from: carteira, to: destinoRegistro, value: '0x0', data: dadosTransacao }]
    });
    const pendente = { identificador: preparo.identificador, assinatura, transacao };
    sessionStorage.setItem(chavePendente, JSON.stringify(pendente));
    await aguardarFinalizacao(pendente);
  } catch (erro) {
    mostrarErroBlockchain(erro.code === 4001 ? 'Operação recusada na MetaMask.' : erro.message);
  } finally {
    assinarBlockchain.disabled = false;
  }
});

provedorCarteira = window.ethereum?.providers?.find(provedor => provedor?.isMetaMask)
  || (window.ethereum?.isMetaMask ? window.ethereum : null);
provedorCarteira?.on?.('accountsChanged', contas => {
  if (carteiraConectada && contas?.[0]?.toLowerCase() !== carteiraConectada.toLowerCase()) {
    operacaoInterrompida = true;
    carteiraConectada = null;
    estadoCarteira.textContent = 'Carteira alterada. Conecte novamente.';
  }
});
provedorCarteira?.on?.('chainChanged', rede => {
  if (rede !== '0xaa36a7') {
    operacaoInterrompida = true;
    estadoCarteira.textContent = 'Rede alterada. Selecione Ethereum Sepolia.';
  }
});

let assinaturaPendente = null;
try {
  assinaturaPendente = JSON.parse(sessionStorage.getItem(chavePendente));
} catch {
  sessionStorage.removeItem(chavePendente);
}
if (assinaturaPendente?.identificador && assinaturaPendente?.assinatura && assinaturaPendente?.transacao) {
  escolherModo(true);
  assinarBlockchain.disabled = true;
  andamentoBlockchain.textContent = 'Retomando verificação da transação...';
  aguardarFinalizacao(assinaturaPendente)
    .catch(erro => mostrarErroBlockchain(erro.message))
    .finally(() => { assinarBlockchain.disabled = false; });
} else {
  sessionStorage.removeItem(chavePendente);
}

window.addEventListener('pagehide', () => {
  if (enderecoPdfBlockchain) URL.revokeObjectURL(enderecoPdfBlockchain);
});
