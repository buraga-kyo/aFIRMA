const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const makeWASocket = require('@whiskeysockets/baileys').default;
const { useMultiFileAuthState, DisconnectReason, downloadMediaMessage } = require('@whiskeysockets/baileys');
const pino = require('pino');
const QRCode = require('qrcode');
process.umask(0o077);

const chave = process.env.APP_CHAT_BRIDGE_KEY || '';
const backend = process.env.APP_CHAT_BACKEND_URL || 'http://127.0.0.1:8080';
const chatwoot = process.env.APP_CHATWOOT_API_URL || '';
const caixaChatwoot = process.env.APP_CHATWOOT_INBOX_ID || '';
const porta = Number(process.env.APP_CHAT_BRIDGE_PORT || 8091);
const diretorio = path.resolve(process.env.APP_CHAT_AUTH_DIR || './dados');
if (chave.length < 32) throw new Error('APP_CHAT_BRIDGE_KEY exige 32 caracteres ou mais');
const sessoes = new Map();
const pendencias = path.join(diretorio, 'pendencias');

function conferirChave(recebida) {
  const a = Buffer.from(chave);
  const b = Buffer.from(recebida || '');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

async function publicar(evento) {
  if (evento.tipo === 'estado') {
    try { await postar(evento); }
    catch (erro) { console.error('Estado não entregue:', erro.message); }
    return;
  }
  await fs.mkdir(pendencias, { recursive: true, mode: 0o700 });
  const arquivo = path.join(pendencias, `${crypto.randomUUID()}.json`);
  await fs.writeFile(arquivo, JSON.stringify(evento), { mode: 0o600 });
  await entregar(arquivo);
}

async function postar(evento) {
  const resposta = await fetch(`${backend}/api/chat/interno/eventos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Chat-Bridge-Key': chave },
    body: JSON.stringify(evento),
    signal: AbortSignal.timeout(10000)
  });
  if (!resposta.ok) throw new Error(`Backend respondeu ${resposta.status}`);
}

async function entregar(arquivo) {
  try {
    const evento = JSON.parse(await fs.readFile(arquivo, 'utf8'));
    await postar(evento);
    if (chatwoot && caixaChatwoot && evento.tipo === 'mensagem') await postarChatwoot(evento);
    await fs.unlink(arquivo);
  } catch (erro) {
    if (erro.code !== 'ENOENT') console.error('Evento pendente:', erro.message);
  }
}

async function pedirChatwoot(caminho, dados) {
  const resposta = await fetch(`${chatwoot.replace(/\/$/, '')}/public/api/v1/inboxes/${encodeURIComponent(caixaChatwoot)}${caminho}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dados),
    signal: AbortSignal.timeout(10000)
  });
  if (!resposta.ok) throw new Error(`Chatwoot respondeu ${resposta.status}`);
  return resposta.json();
}

async function postarChatwoot(evento) {
  const contato = `${evento.canalId}-${evento.telefone}`;
  await pedirChatwoot('/contacts', {
    source_id: contato,
    identifier: contato,
    name: evento.nome || evento.telefone,
    phone_number: `+${evento.telefone}`
  });
  const conversa = await pedirChatwoot(`/contacts/${encodeURIComponent(contato)}/conversations`, {});
  await pedirChatwoot(`/contacts/${encodeURIComponent(contato)}/conversations/${conversa.id}/messages`, {
    content: evento.texto,
    source_id: `${evento.canalId}-${evento.identificador}`
  });
}

async function reenviarPendencias() {
  await fs.mkdir(pendencias, { recursive: true, mode: 0o700 });
  for (const nome of await fs.readdir(pendencias)) {
    if (nome.endsWith('.json')) await entregar(path.join(pendencias, nome));
  }
}

function textoMensagem(mensagem) {
  const corpo = mensagem.message;
  return corpo?.conversation || corpo?.extendedTextMessage?.text ||
    corpo?.imageMessage?.caption || corpo?.videoMessage?.caption || null;
}

async function conectar(id) {
  if (sessoes.has(id)) return;
  const pasta = path.join(diretorio, id);
  await fs.mkdir(pasta, { recursive: true, mode: 0o700 });
  const { state, saveCreds } = await useMultiFileAuthState(pasta);
  const sessao = { cliente: null, qr: null, estado: 'CONECTANDO', reiniciar: true };
  sessoes.set(id, sessao);
  await publicar({ tipo: 'estado', canalId: id, estado: 'CONECTANDO' });
  let cliente;
  try { cliente = makeWASocket({
    auth: state,
    printQRInTerminal: false,
    markOnlineOnConnect: false,
    logger: pino({ level: 'silent' })
  }); } catch (erro) { sessoes.delete(id); throw erro; }
  sessao.cliente = cliente;
  setTimeout(() => {
    if (sessoes.get(id) === sessao && !sessao.cliente.user)
      desconectar(id).catch(erro => console.error('QR expirado:', erro));
  }, 600000).unref();
  cliente.ev.on('creds.update', saveCreds);
  cliente.ev.on('connection.update', async ({ connection, qr, lastDisconnect }) => {
    try {
      if (qr) {
        sessao.qr = qr;
        sessao.estado = 'QR_PENDENTE';
        await publicar({ tipo: 'estado', canalId: id, estado: 'QR_PENDENTE' });
      }
      if (connection === 'open') {
        sessao.qr = null;
        sessao.estado = 'CONECTADO';
        await publicar({ tipo: 'estado', canalId: id, estado: 'CONECTADO' });
      }
      if (connection === 'close') {
        if (sessoes.get(id) === sessao) sessoes.delete(id);
        await publicar({ tipo: 'estado', canalId: id, estado: 'DESCONECTADO' });
        const codigo = lastDisconnect?.error?.output?.statusCode;
        if (sessao.reiniciar && codigo !== DisconnectReason.loggedOut) {
          setTimeout(() => conectar(id).catch(console.error), 3000);
        }
      }
    } catch (erro) { console.error('Estado de canal:', erro); }
  });
  cliente.ev.on('messages.upsert', async ({ messages, type }) => {
    if (type !== 'notify') return;
    for (const mensagem of messages) {
      const jid = mensagem.key.remoteJid || '';
      const texto = textoMensagem(mensagem);
      const corpo = mensagem.message || {};
      const midia = corpo.imageMessage || corpo.videoMessage || corpo.audioMessage || corpo.documentMessage;
      if (mensagem.key.fromMe || !jid.endsWith('@s.whatsapp.net') || (!texto && !midia)) continue;
      const telefone = jid.split('@')[0];
      if (!/^\d{8,15}$/.test(telefone) || !mensagem.key.id) continue;
      const evento = { tipo: 'mensagem', canalId: id, identificador: mensagem.key.id,
        telefone, nome: mensagem.pushName || null, texto: texto || '[anexo]' };
      if (midia) {
        try {
          if (Number(midia.fileLength || 0) > 15 * 1024 * 1024) throw new Error('Arquivo grande demais');
          const conteudo = await downloadMediaMessage(mensagem, 'buffer', {},
            { logger: pino({ level: 'silent' }), reuploadRequest: cliente.updateMediaMessage });
          if (conteudo.length > 15 * 1024 * 1024) throw new Error('Arquivo grande demais');
          evento.conteudo = conteudo.toString('base64');
          evento.tipoMime = midia.mimetype || 'application/octet-stream';
          evento.nomeArquivo = midia.fileName || `anexo-${mensagem.key.id}`;
        } catch (erro) { console.error('Anexo recebido:', erro.message); }
      }
      await publicar(evento);
    }
  });
}

async function desconectar(id) {
  const sessao = sessoes.get(id);
  if (!sessao) return;
  sessao.reiniciar = false;
  sessoes.delete(id);
  try { sessao.cliente.end(undefined); }
  finally { await publicar({ tipo: 'estado', canalId: id, estado: 'DESCONECTADO' }); }
}

async function corpoJson(requisicao, limite = 16384) {
  let texto = '';
  for await (const parte of requisicao) {
    texto += parte;
    if (texto.length > limite) throw new Error('Corpo grande demais');
  }
  return JSON.parse(texto);
}

function respostaJson(resposta, estado, valor) {
  resposta.writeHead(estado, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  resposta.end(JSON.stringify(valor));
}

http.createServer(async (requisicao, resposta) => {
  if (!conferirChave(requisicao.headers['x-chat-bridge-key'])) {
    respostaJson(resposta, 401, { erro: 'Não autorizado' });
    return;
  }
  const partes = new URL(requisicao.url, 'http://localhost').pathname.split('/');
  const id = partes[2];
  if (partes[1] !== 'sessoes' || !/^[0-9a-f-]{36}$/i.test(id)) {
    respostaJson(resposta, 404, { erro: 'Rota não encontrada' });
    return;
  }
  try {
    if (requisicao.method === 'POST' && partes.length === 3) {
      await conectar(id);
      respostaJson(resposta, 200, {});
    } else if (requisicao.method === 'DELETE' && partes.length === 3) {
      await desconectar(id);
      respostaJson(resposta, 200, {});
    } else if (requisicao.method === 'GET' && partes[3] === 'qr') {
      const qr = sessoes.get(id)?.qr;
      respostaJson(resposta, qr ? 200 : 404, { qr: qr ? await QRCode.toDataURL(qr) : '' });
    } else if (requisicao.method === 'GET' && partes[3] === 'estado') {
      respostaJson(resposta, 200, { estado: sessoes.get(id)?.estado || 'DESCONECTADO' });
    } else if (requisicao.method === 'POST' && partes[3] === 'mensagens') {
      const dados = await corpoJson(requisicao);
      if (!/^\d{8,15}$/.test(dados.telefone) || typeof dados.texto !== 'string' ||
          !dados.texto.trim() || dados.texto.length > 4096) throw new Error('Mensagem inválida');
      const cliente = sessoes.get(id)?.cliente;
      if (!cliente?.user) throw new Error('Canal desconectado');
      const enviada = await cliente.sendMessage(`${dados.telefone}@s.whatsapp.net`, { text: dados.texto });
      respostaJson(resposta, 200, { identificador: enviada.key.id });
    } else if (requisicao.method === 'POST' && partes[3] === 'arquivos') {
      const dados = await corpoJson(requisicao, 22 * 1024 * 1024);
      if (!/^\d{8,15}$/.test(dados.telefone) || !/^[\w.+-]+\/[\w.+-]+$/.test(dados.tipoMime) ||
          typeof dados.conteudo !== 'string' || dados.conteudo.length > 21 * 1024 * 1024 ||
          typeof dados.nome !== 'string' || dados.nome.length > 255) throw new Error('Arquivo inválido');
      const cliente = sessoes.get(id)?.cliente;
      if (!cliente?.user) throw new Error('Canal desconectado');
      const conteudo = Buffer.from(dados.conteudo, 'base64');
      if (!conteudo.length || conteudo.length > 15 * 1024 * 1024) throw new Error('Arquivo inválido');
      const tipo = dados.tipoMime.startsWith('image/') ? 'image' :
        dados.tipoMime.startsWith('video/') ? 'video' :
        dados.tipoMime.startsWith('audio/') ? 'audio' : 'document';
      const arquivo = { [tipo]: conteudo, mimetype: dados.tipoMime };
      if (tipo === 'document') arquivo.fileName = dados.nome;
      if (dados.legenda && tipo !== 'audio' && tipo !== 'document') arquivo.caption = dados.legenda;
      const enviada = await cliente.sendMessage(`${dados.telefone}@s.whatsapp.net`, arquivo);
      respostaJson(resposta, 200, { identificador: enviada.key.id });
    } else respostaJson(resposta, 404, { erro: 'Rota não encontrada' });
  } catch (erro) {
    console.error('Operação WhatsApp:', erro);
    respostaJson(resposta, 503, { erro: 'Operação WhatsApp falhou' });
  }
}).listen(porta, '127.0.0.1', () => {
  reenviarPendencias().catch(console.error);
  setInterval(() => reenviarPendencias().catch(console.error), 5000).unref();
  console.log(`Ponte WhatsApp em 127.0.0.1:${porta}`);
});
