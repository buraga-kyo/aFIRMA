const servidorHttp = require('node:http');
const arquivosEmDisco = require('node:fs/promises');
const caminhos = require('node:path');
const criptografia = require('node:crypto');
const criarConexaoWhatsApp = require('@whiskeysockets/baileys').default;
const {
  useMultiFileAuthState: usarArquivosDaSessao,
  DisconnectReason: motivosDesconexao,
  downloadMediaMessage: baixarMidia
} = require('@whiskeysockets/baileys');
const criarRegistro = require('pino');
const codigoQr = require('qrcode');
const {
  Browsers: navegadores
} = require('@whiskeysockets/baileys');
const {
  HistoricoWhatsApp
} = require('./historico.cjs');
const {
  MotorChatFirma
} = require('./motor-chat-firma.cjs');
const {
  configuracaoConexao,
  mensagemFalha,
  podeReconectar,
  PrazoConexao
} = require('./conexao.cjs');
process.umask(0o077);
const chave = process.env.APP_CHAT_BRIDGE_KEY || '';
const enderecoServidor = process.env.APP_CHAT_BACKEND_URL || 'http://127.0.0.1:8080';
const enderecoMotorChat = process.env.APP_CHATWOOT_API_URL || '';
const caixaDoMotor = process.env.APP_CHATWOOT_INBOX_ID || '';
const porta = Number(process.env.APP_CHAT_BRIDGE_PORT || 8091);
const diretorio = caminhos.resolve(process.env.APP_CHAT_AUTH_DIR || './dados');
if (chave.length < 32) throw new Error('APP_CHAT_BRIDGE_KEY exige 32 caracteres ou mais');
const motorChat = new MotorChatFirma({
  base: enderecoMotorChat,
  caixa: caixaDoMotor,
  segredoIdentidade: process.env.APP_CHATWOOT_HMAC_TOKEN || '',
  diretorio: caminhos.join(diretorio, 'motor-chat')
});
const sessoes = new Map();
const pendencias = caminhos.join(diretorio, 'pendencias');
const historico = new HistoricoWhatsApp({
  diretorio,
  publicar,
  textoMensagem
});
function conferirChave(recebida) {
  const registro = Buffer.from(chave);
  const botao = Buffer.from(recebida || '');
  return registro.length === botao.length && criptografia.timingSafeEqual(registro, botao);
}
async function publicar(evento) {
  if (evento.tipo === 'estado') {
    try {
      await postar(evento);
    } catch (erro) {
      console.error('Estado não entregue:', erro.message);
    }
    return;
  }
  await arquivosEmDisco.mkdir(pendencias, {
    recursive: true,
    mode: 0o700
  });
  const arquivo = caminhos.join(pendencias, `${criptografia.randomUUID()}.json`);
  await arquivosEmDisco.writeFile(arquivo, JSON.stringify(evento), {
    mode: 0o600
  });
  await entregar(arquivo);
}
async function postar(evento) {
  const resposta = await fetch(`${enderecoServidor}/api/chat/interno/eventos`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Chat-Bridge-Key': chave
    },
    body: JSON.stringify(evento),
    signal: AbortSignal.timeout(10000)
  });
  if (!resposta.ok) throw new Error(`Backend respondeu ${resposta.status}`);
}
async function entregar(arquivo) {
  try {
    const evento = JSON.parse(await arquivosEmDisco.readFile(arquivo, 'utf8'));
    await postar(evento);
    if (enderecoMotorChat && caixaDoMotor && evento.tipo === 'mensagem') await encaminharAoMotor(evento);
    await arquivosEmDisco.unlink(arquivo);
  } catch (erro) {
    if (erro.code !== 'ENOENT') console.error('Evento pendente:', erro.message);
  }
}
async function encaminharAoMotor(evento) {
  await motorChat.encaminhar(evento);
}
async function reenviarPendencias() {
  await arquivosEmDisco.mkdir(pendencias, {
    recursive: true,
    mode: 0o700
  });
  for (const nome of await arquivosEmDisco.readdir(pendencias)) {
    if (nome.endsWith('.json')) await entregar(caminhos.join(pendencias, nome));
  }
}
function textoMensagem(mensagem) {
  const corpo = mensagem.message;
  return corpo?.conversation || corpo?.extendedTextMessage?.text || corpo?.imageMessage?.caption || corpo?.videoMessage?.caption || null;
}
async function conectar(id, tentativa = 0) {
  const anterior = sessoes.get(id);
  if (anterior && anterior.estado !== 'DESCONECTADO') return;
  anterior?.prazo?.cancelar();
  const sessao = {
    cliente: null,
    qr: null,
    estado: 'CONECTANDO',
    reiniciar: true,
    erro: null,
    codigo: null,
    jaConectou: false
  };
  // Reserva a conexão antes de abrir os arquivos, para duas abas não conectarem o mesmo canal juntas.
  sessoes.set(id, sessao);
  const falhar = async (codigo, mensagem) => {
    if (sessoes.get(id) !== sessao) return;
    sessao.prazo?.cancelar();
    sessao.reiniciar = false;
    sessao.estado = 'DESCONECTADO';
    sessao.qr = null;
    sessao.codigo = codigo;
    sessao.erro = mensagem;
    sessao.cliente?.end(undefined);
    await publicar({
      tipo: 'estado',
      canalId: id,
      estado: 'DESCONECTADO'
    });
  };
  sessao.prazo = new PrazoConexao(() => falhar(408, sessao.qr ? 'O QR expirou. Gere um novo código.' : 'O WhatsApp não gerou o QR em 45 segundos. Tente novamente.').catch(console.error));
  let cliente, salvarCredenciais;
  try {
    const pasta = caminhos.join(diretorio, id);
    await arquivosEmDisco.mkdir(pasta, {
      recursive: true,
      mode: 0o700
    });
    const credenciais = await usarArquivosDaSessao(pasta);
    salvarCredenciais = credenciais.saveCreds;
    if (sessoes.get(id) !== sessao || !sessao.reiniciar) return;
    await publicar({
      tipo: 'estado',
      canalId: id,
      estado: 'CONECTANDO'
    });
    cliente = criarConexaoWhatsApp({
      auth: credenciais.state,
      ...configuracaoConexao(navegadores),
      printQRInTerminal: false,
      markOnlineOnConnect: false,
      logger: criarRegistro({
        level: 'silent'
      })
    });
    sessao.cliente = cliente;
  } catch (erro) {
    await falhar(null, 'Não foi possível iniciar a conexão WhatsApp.');
    throw erro;
  }
  cliente.ev.on('creds.update', salvarCredenciais);
  let filaHistorico = Promise.resolve();
  cliente.ev.on('messaging-history.set', dadosEnviados => {
    filaHistorico = filaHistorico.then(() => historico.receber(id, dadosEnviados)).catch(eventoAtual => historico.erro(id, eventoAtual).catch(console.error));
  });
  cliente.ev.on('contacts.upsert', contatos => {
    filaHistorico = filaHistorico.then(() => historico.contatos(id, contatos)).catch(eventoAtual => console.error('Contatos do histórico:', eventoAtual.message));
  });
  cliente.ev.on('connection.update', async ({
    connection: estadoConexao,
    qr,
    lastDisconnect: ultimaDesconexao
  }) => {
    try {
      if (sessoes.get(id) !== sessao || !sessao.reiniciar) return;
      if (qr) {
        sessao.prazo.qr();
        sessao.erro = null;
        sessao.qr = qr;
        sessao.estado = 'QR_PENDENTE';
        await publicar({
          tipo: 'estado',
          canalId: id,
          estado: 'QR_PENDENTE'
        });
      }
      if (estadoConexao === 'open') {
        sessao.prazo.cancelar();
        sessao.jaConectou = true;
        sessao.erro = null;
        sessao.qr = null;
        sessao.estado = 'CONECTADO';
        await publicar({
          tipo: 'estado',
          canalId: id,
          estado: 'CONECTADO'
        });
      }
      if (estadoConexao === 'close') {
        sessao.prazo.cancelar();
        sessao.qr = null;
        const codigo = ultimaDesconexao?.error?.output?.statusCode;
        console.error('Conexão WhatsApp encerrada:', codigo || 'sem código');
        if (sessao.reiniciar && podeReconectar(codigo, sessao.jaConectou, tentativa)) {
          sessao.estado = 'CONECTANDO';
          setTimeout(() => {
            if (sessoes.get(id) !== sessao || !sessao.reiniciar) return;
            sessoes.delete(id);
            conectar(id, tentativa + 1).catch(console.error);
          }, 1500).unref();
        } else await falhar(codigo || null, mensagemFalha(codigo));
      }
    } catch (erro) {
      console.error('Estado de canal:', erro);
    }
  });
  cliente.ev.on('messages.update', async alteracoes => {
    for (const {
      key: chaveMensagem,
      update: alteracao
    } of alteracoes) {
      if (!chaveMensagem?.id || alteracao?.status == null) continue;
      await publicar({
        tipo: 'atualizacao',
        canalId: id,
        identificador: chaveMensagem.id,
        ack: Number(alteracao.status) - 1
      });
    }
  });
  cliente.ev.on('messages.upsert', async ({
    messages: mensagens,
    type: tipoAtual
  }) => {
    if (tipoAtual !== 'notify') return;
    for (const mensagem of mensagens) {
      const jid = mensagem.key.remoteJid || '';
      const texto = textoMensagem(mensagem);
      const corpo = mensagem.message || {};
      const midia = corpo.imageMessage || corpo.videoMessage || corpo.audioMessage || corpo.documentMessage;
      const protocolo = corpo.protocolMessage;
      if (protocolo?.key?.id) {
        const editada = protocolo.editedMessage?.conversation || protocolo.editedMessage?.extendedTextMessage?.text;
        await publicar({
          tipo: 'atualizacao',
          canalId: id,
          identificador: protocolo.key.id,
          deletada: Number(protocolo.type) === 0,
          editada: editada != null,
          texto: editada || null
        });
        continue;
      }
      if (corpo.reactionMessage?.key?.id) {
        await publicar({
          tipo: 'atualizacao',
          canalId: id,
          identificador: corpo.reactionMessage.key.id,
          reacao: {
            Numero: (mensagem.key.participant || jid).replace(/\D/g, ''),
            Reacao: corpo.reactionMessage.text || ''
          }
        });
        continue;
      }
      if (!jid.endsWith('@s.whatsapp.net') || !texto && !midia) continue;
      const telefone = jid.split('@')[0];
      if (!/^\d{8,15}$/.test(telefone) || !mensagem.key.id) continue;
      const evento = {
        tipo: 'mensagem',
        canalId: id,
        identificador: mensagem.key.id,
        telefone,
        nome: mensagem.pushName || null,
        texto: texto || '[anexo]',
        fromMe: mensagem.key.fromMe === true,
        raw: {
          key: mensagem.key,
          message: corpo
        },
        respostaId: (corpo.extendedTextMessage || midia)?.contextInfo?.stanzaId || null
      };
      if (JSON.stringify(evento.raw).length > 180000) evento.raw = {
        key: mensagem.key,
        message: {
          conversation: texto || '[anexo]'
        }
      };
      if (midia) {
        try {
          if (Number(midia.fileLength || 0) > 15 * 1024 * 1024) throw new Error('Arquivo grande demais');
          const conteudo = await baixarMidia(mensagem, 'buffer', {}, {
            logger: criarRegistro({
              level: 'silent'
            }),
            reuploadRequest: cliente.updateMediaMessage
          });
          if (conteudo.length > 15 * 1024 * 1024) throw new Error('Arquivo grande demais');
          evento.conteudo = conteudo.toString('base64');
          evento.tipoMime = midia.mimetype || 'application/octet-stream';
          evento.nomeArquivo = midia.fileName || `anexo-${mensagem.key.id}`;
        } catch (erro) {
          console.error('Anexo recebido:', erro.message);
        }
      }
      await publicar(evento);
    }
  });
}
async function desconectar(id) {
  const sessao = sessoes.get(id);
  if (!sessao) return;
  sessao.reiniciar = false;
  sessao.prazo?.cancelar();
  sessoes.delete(id);
  try {
    sessao.cliente.end(undefined);
  } finally {
    await publicar({
      tipo: 'estado',
      canalId: id,
      estado: 'DESCONECTADO'
    });
  }
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
  resposta.writeHead(estado, {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-store'
  });
  resposta.end(JSON.stringify(valor));
}
servidorHttp.createServer(async (requisicao, resposta) => {
  if (!conferirChave(requisicao.headers['x-chat-bridge-key'])) {
    respostaJson(resposta, 401, {
      erro: 'Não autorizado'
    });
    return;
  }
  const partes = new URL(requisicao.url, 'http://localhost').pathname.split('/');
  const id = partes[2];
  if (partes[1] !== 'sessoes' || !/^[0-9a-f-]{36}$/i.test(id)) {
    respostaJson(resposta, 404, {
      erro: 'Rota não encontrada'
    });
    return;
  }
  try {
    if (requisicao.method === 'POST' && partes.length === 3) {
      await conectar(id);
      respostaJson(resposta, 200, {});
    } else if (requisicao.method === 'DELETE' && partes.length === 3) {
      await desconectar(id);
      respostaJson(resposta, 200, {});
    } else if (requisicao.method === 'GET' && partes[3] === 'conexao') {
      const item = sessoes.get(id);
      respostaJson(resposta, 200, {
        estado: item?.estado || 'DESCONECTADO',
        qr: item?.qr ? await codigoQr.toDataURL(item.qr) : '',
        erro: item?.erro || null,
        codigo: item?.codigo || null
      });
    } else if (partes[3] === 'historico' && ['GET', 'POST'].includes(requisicao.method)) {
      if (requisicao.method === 'POST') await historico.pedir(id, sessoes.get(id)?.cliente);
      respostaJson(resposta, 200, await historico.status(id));
    } else if (requisicao.method === 'GET' && partes[3] === 'qr') {
      const qr = sessoes.get(id)?.qr;
      respostaJson(resposta, qr ? 200 : 404, {
        qr: qr ? await codigoQr.toDataURL(qr) : ''
      });
    } else if (requisicao.method === 'GET' && partes[3] === 'estado') {
      respostaJson(resposta, 200, {
        estado: sessoes.get(id)?.estado || 'DESCONECTADO'
      });
    } else if (requisicao.method === 'POST' && partes[3] === 'foto') {
      const dados = await corpoJson(requisicao);
      if (!/^\d{8,15}$/.test(dados.telefone)) throw new Error('Telefone inválido');
      const cliente = sessoes.get(id)?.cliente;
      if (!cliente?.user) throw new Error('Canal desconectado');
      let foto = null;
      try {
        foto = await cliente.profilePictureUrl(`${dados.telefone}@s.whatsapp.net`, 'image');
      } catch (erro) {
        if (erro.output?.statusCode !== 404) throw erro;
      }
      respostaJson(resposta, 200, {
        foto
      });
    } else if (requisicao.method === 'POST' && partes[3] === 'mensagens') {
      const dados = await corpoJson(requisicao);
      if (!/^\d{8,15}$/.test(dados.telefone) || typeof dados.texto !== 'string' || !dados.texto.trim() || dados.texto.length > 4096) throw new Error('Mensagem inválida');
      const cliente = sessoes.get(id)?.cliente;
      if (!cliente?.user) throw new Error('Canal desconectado');
      const jid = `${dados.telefone}@s.whatsapp.net`;
      const mensagemRespondida = typeof dados.resposta === 'string' && dados.resposta ? {
        key: {
          remoteJid: jid,
          id: dados.resposta,
          fromMe: false
        },
        message: {
          conversation: ''
        }
      } : undefined;
      const enviada = await cliente.sendMessage(jid, {
        text: dados.texto
      }, mensagemRespondida ? {
        quoted: mensagemRespondida
      } : undefined);
      respostaJson(resposta, 200, {
        identificador: enviada.key.id
      });
    } else if (requisicao.method === 'POST' && ['lida', 'remover'].includes(partes[3])) {
      const dados = await corpoJson(requisicao);
      if (!/^\d{8,15}$/.test(dados.telefone) || typeof dados.identificador !== 'string' || !dados.identificador || dados.identificador.length > 200) throw new Error('Mensagem inválida');
      const cliente = sessoes.get(id)?.cliente;
      if (!cliente?.user) throw new Error('Canal desconectado');
      const chaveMensagem = {
        remoteJid: `${dados.telefone}@s.whatsapp.net`,
        id: dados.identificador,
        fromMe: dados.fromMe === true
      };
      if (partes[3] === 'lida') await cliente.readMessages([chaveMensagem]);else {
        if (!chaveMensagem.fromMe) throw new Error('Somente mensagens enviadas podem ser apagadas');
        await cliente.sendMessage(chaveMensagem.remoteJid, {
          delete: chaveMensagem
        });
      }
      respostaJson(resposta, 200, {});
    } else if (requisicao.method === 'POST' && partes[3] === 'arquivos') {
      const dados = await corpoJson(requisicao, 22 * 1024 * 1024);
      if (!/^\d{8,15}$/.test(dados.telefone) || !/^[\w.+-]+\/[\w.+-]+$/.test(dados.tipoMime) || typeof dados.conteudo !== 'string' || dados.conteudo.length > 21 * 1024 * 1024 || typeof dados.nome !== 'string' || dados.nome.length > 255) throw new Error('Arquivo inválido');
      const cliente = sessoes.get(id)?.cliente;
      if (!cliente?.user) throw new Error('Canal desconectado');
      const conteudo = Buffer.from(dados.conteudo, 'base64');
      if (!conteudo.length || conteudo.length > 15 * 1024 * 1024) throw new Error('Arquivo inválido');
      const tipo = dados.tipoMime.startsWith('image/') ? 'image' : dados.tipoMime.startsWith('video/') ? 'video' : dados.tipoMime.startsWith('audio/') ? 'audio' : 'document';
      const arquivo = {
        [tipo]: conteudo,
        mimetype: dados.tipoMime
      };
      if (tipo === 'document') arquivo.fileName = dados.nome;
      if (dados.legenda && tipo !== 'audio' && tipo !== 'document') arquivo.caption = dados.legenda;
      const enviada = await cliente.sendMessage(`${dados.telefone}@s.whatsapp.net`, arquivo);
      respostaJson(resposta, 200, {
        identificador: enviada.key.id
      });
    } else respostaJson(resposta, 404, {
      erro: 'Rota não encontrada'
    });
  } catch (erro) {
    console.error('Operação WhatsApp:', erro);
    respostaJson(resposta, 503, {
      erro: 'Operação WhatsApp falhou'
    });
  }
}).listen(porta, '127.0.0.1', () => {
  reenviarPendencias().catch(console.error);
  setInterval(() => reenviarPendencias().catch(console.error), 5000).unref();
  console.log(`Ponte WhatsApp em 127.0.0.1:${porta}`);
});
