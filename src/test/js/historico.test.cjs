const {
  test: teste
} = require('node:test');
const assert = require('node:assert/strict');
const arquivosEmDisco = require('node:fs/promises'),
  os = require('node:os'),
  caminhos = require('node:path');
const {
  HistoricoWhatsApp
} = require('../../../chat-baileys/historico.cjs');
teste('normaliza histórico, resolve LID, conserva datas e sentidos e separa lotes sem enviar respostas', async () => {
  const diretorio = await arquivosEmDisco.mkdtemp(caminhos.join(os.tmpdir(), 'firma-historico-')),
    eventos = [];
  try {
    const cabecalho = new HistoricoWhatsApp({
      diretorio,
      publicar: async evento => eventos.push(evento),
      textoMensagem: item => item.message?.conversation
    });
    const canal = '11111111-1111-1111-1111-111111111111';
    const mensagens = Array.from({
      length: 105
    }, (_, indice) => ({
      key: {
        id: 'mensagem-' + indice,
        remoteJid: '123@lid',
        fromMe: indice % 2 === 0
      },
      messageTimestamp: 1700000000 + indice,
      message: {
        conversation: 'Histórico ' + indice
      },
      pushName: indice % 2 === 0 ? 'Meu nome' : 'Marina'
    }));
    mensagens.push({
      key: {
        id: 'grupo',
        remoteJid: '123@g.us'
      },
      messageTimestamp: 1700000000,
      message: {
        conversation: 'Grupo'
      }
    });
    await cabecalho.receber(canal, {
      contacts: [{
        id: '5511999990001@s.whatsapp.net',
        lid: '123@lid',
        name: 'Marina'
      }],
      chats: [{
        id: '123@lid'
      }],
      messages: mensagens,
      isLatest: true,
      progress: 100
    });
    assert.equal(eventos.filter(evento => evento.raw.mensagens.length).length, 3);
    assert.equal(eventos.flatMap(evento => evento.raw.mensagens).length, 105);
    assert.equal(eventos[0].raw.conversas[0].nome, 'Marina');
    const mensagem = eventos[1].raw.mensagens[0];
    assert.equal(mensagem.telefone, '5511999990001');
    assert.equal(mensagem.timestamp, 1700000000);
    assert.equal(mensagem.fromMe, true);
    assert.ok(eventos.every(evento => evento.tipo === 'historico'));
    assert.equal(eventos.at(-1).raw.ignoradas, 1);
    const reaberto = new HistoricoWhatsApp({
        diretorio,
        publicar: async () => {},
        textoMensagem: () => ''
      }),
      pedidos = [];
    await reaberto.pedir(canal, {
      user: {
        id: 'me'
      },
      fetchMessageHistory: async (...registro) => pedidos.push(registro)
    });
    await reaberto.trabalho;
    assert.equal(pedidos.length, 1);
    assert.equal(pedidos[0][2], 1700000000);
    assert.equal((await reaberto.status(canal)).recebidos, 105);
    await assert.rejects(() => reaberto.pedir(canal, null), /Conecte/);
  } finally {
    await arquivosEmDisco.rm(diretorio, {
      recursive: true,
      force: true
    });
  }
});
teste('não usa o nome do proprietário em contatos e identifica mídia indisponível', async () => {
  const diretorio = await arquivosEmDisco.mkdtemp(caminhos.join(os.tmpdir(), 'firma-historico-')),
    eventos = [];
  try {
    const cabecalho = new HistoricoWhatsApp({
      diretorio,
      publicar: async evento => eventos.push(evento),
      textoMensagem: () => null
    });
    await cabecalho.receber('canal', {
      messages: [{
        key: {
          id: 'arquivo',
          remoteJid: '5511999990002@s.whatsapp.net',
          fromMe: true
        },
        pushName: 'Proprietário',
        messageTimestamp: 1700000000,
        message: {
          imageMessage: {}
        }
      }]
    });
    const mensagem = eventos.flatMap(evento => evento.raw.mensagens)[0];
    assert.equal(mensagem.nome, '');
    assert.match(mensagem.texto, /mídia não disponibilizada/);
  } finally {
    await arquivosEmDisco.rm(diretorio, {
      recursive: true,
      force: true
    });
  }
});
