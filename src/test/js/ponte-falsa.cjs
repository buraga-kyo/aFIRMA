// Transporte exclusivamente de teste: escuta só no loopback e nunca carrega Baileys.
const servidorHttp = require('node:http'),
  criptografia = require('node:crypto');
const qrTeste = require('../../../chat-baileys/node_modules/qrcode').toDataURL('FIRMA: QR SIMULADO, SEM CONEXAO REAL');
const chave = process.env.FIRMA_TEST_BRIDGE_KEY || 'firma-ponte-local-de-testes-2026-chave',
  envios = [],
  contagens = new Map(),
  conexoes = new Map();
const server = servidorHttp.createServer(async (requisicao, resposta) => {
  resposta.setHeader('Content-Type', 'application/json');
  if (requisicao.url === '/_envios') {
    resposta.end(JSON.stringify(envios));
    return;
  }
  if (requisicao.headers['x-chat-bridge-key'] !== chave) {
    resposta.statusCode = 401;
    resposta.end('{}');
    return;
  }
  let texto = '';
  for await (const paragrafo of requisicao) texto += paragrafo;
  let dados = {};
  try {
    dados = texto ? JSON.parse(texto) : {};
  } catch {
    resposta.statusCode = 400;
    resposta.end('{}');
    return;
  }
  if (requisicao.url.endsWith('/mensagens') || requisicao.url.endsWith('/arquivos')) {
    const chaveAtual = dados.texto || dados.legenda || '',
      vez = (contagens.get(chaveAtual) || 0) + 1;
    contagens.set(chaveAtual, vez);
    if (chaveAtual.includes('falhar') && vez === 1) {
      resposta.statusCode = 503;
      resposta.end('{"erro":"falha simulada"}');
      return;
    }
    envios.push({
      ...dados,
      instante: Date.now()
    });
    resposta.end(JSON.stringify({
      identificador: 'simulado-' + criptografia.randomUUID()
    }));
  } else if (requisicao.url.endsWith('/conexao')) {
    const indice = (conexoes.get(requisicao.url) || 0) + 1;
    conexoes.set(requisicao.url, indice);
    resposta.end(JSON.stringify({
      estado: indice < 2 ? 'CONECTANDO' : indice < 4 ? 'QR_PENDENTE' : 'CONECTADO',
      qr: indice >= 2 && indice < 4 ? await qrTeste : ''
    }));
  } else if (requisicao.url.endsWith('/historico')) resposta.end(JSON.stringify({
    mensagem: requisicao.method === 'POST' ? 'Solicitação de teste enviada.' : 'Histórico de teste recebido.',
    progresso: 100,
    falhas: 0
  }));else if (requisicao.url.endsWith('/estado')) resposta.end('{"estado":"CONECTADO"}');else if (requisicao.url.endsWith('/qr')) resposta.end(JSON.stringify({
    qr: await qrTeste
  }));else resposta.end('{}');
});
server.listen(Number(process.env.FIRMA_TEST_BRIDGE_PORT || 18091), '127.0.0.1', () => console.log('Ponte simulada local em ' + server.address().port));
