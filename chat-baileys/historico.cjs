const arquivosEmDisco = require('node:fs/promises');
const caminhos = require('node:path');
const criptografia = require('node:crypto');

// O WhatsApp entrega os lotes aqui. A ponte passa esses lotes ao Java para guardar o histórico.
class HistoricoWhatsApp {
  constructor({
    diretorio,
    publicar,
    textoMensagem
  }) {
    this.diretorio = diretorio;
    this.publicar = publicar;
    this.textoMensagem = textoMensagem;
    this.canais = new Map();
  }
  async carregar(id) {
    if (!this.canais.has(id)) {
      let estado = {
        contatos: {},
        cursores: {},
        recebidos: 0,
        pedidos: 0,
        mensagem: 'Aguardando sincronização do WhatsApp.'
      };
      try {
        estado = {
          ...estado,
          ...JSON.parse(await arquivosEmDisco.readFile(this.arquivo(id), 'utf8'))
        };
      } catch (evento) {
        if (evento.code !== 'ENOENT') throw evento;
      }
      estado.solicitando = false;
      this.canais.set(id, estado);
    }
    return this.canais.get(id);
  }
  arquivo(id) {
    return caminhos.join(this.diretorio, id, 'historico.json');
  }
  async salvar(id) {
    await arquivosEmDisco.mkdir(caminhos.dirname(this.arquivo(id)), {
      recursive: true,
      mode: 0o700
    });
    const arquivoTemporario = this.arquivo(id) + '.' + criptografia.randomUUID() + '.tmp';
    await arquivosEmDisco.writeFile(arquivoTemporario, JSON.stringify(await this.carregar(id)), {
      mode: 0o600
    });
    await arquivosEmDisco.rename(arquivoTemporario, this.arquivo(id));
  }
  async contatos(id, contatos) {
    const evento = await this.carregar(id);
    for (const item of contatos || []) {
      const jid = item.jid || item.phoneNumber || item.id;
      if (!/^\d{8,15}@s\.whatsapp\.net$/.test(jid || '')) continue;
      const contato = {
        jid,
        nome: item.name || item.notify || item.verifiedName || ''
      };
      evento.contatos[item.id] = contato;
      if (item.lid) evento.contatos[item.lid] = contato;
      evento.contatos[jid] = contato;
    }
    await this.salvar(id);
  }
  async receber(id, dadosEnviados) {
    await this.contatos(id, dadosEnviados.contacts);
    const evento = await this.carregar(id),
      mensagens = [],
      conversas = new Map();
    let ignoradas = 0;
    for (const item of dadosEnviados.chats || []) {
      const jid = evento.contatos[item.id]?.jid || item.id;
      if (!/^\d{8,15}@s\.whatsapp\.net$/.test(jid || '')) {
        ignoradas++;
        continue;
      }
      conversas.set(jid, {
        telefone: jid.split('@')[0],
        nome: evento.contatos[jid]?.nome || item.name || ''
      });
    }
    for (const itemAtual of dadosEnviados.messages || []) {
      const jid = evento.contatos[itemAtual.key?.remoteJid]?.jid || itemAtual.key?.remoteJid;
      const instante = Number(itemAtual.messageTimestamp?.toString() || 0);
      const corpo = itemAtual.message?.ephemeralMessage?.message || itemAtual.message?.viewOnceMessage?.message || itemAtual.message || {};
      if (!/^\d{8,15}@s\.whatsapp\.net$/.test(jid || '') || !itemAtual.key?.id || !instante || corpo.protocolMessage || corpo.reactionMessage) {
        ignoradas++;
        continue;
      }
      const midia = corpo.imageMessage || corpo.videoMessage || corpo.audioMessage || corpo.documentMessage || corpo.stickerMessage;
      const texto = this.textoMensagem({
        ...itemAtual,
        message: corpo
      });
      if (!texto && !midia) {
        ignoradas++;
        continue;
      }
      const nome = evento.contatos[jid]?.nome || (!itemAtual.key.fromMe ? itemAtual.pushName : '') || '';
      const tipo = corpo.imageMessage ? 'Imagem' : corpo.videoMessage ? 'Vídeo' : corpo.audioMessage ? 'Áudio' : corpo.documentMessage ? 'Documento' : corpo.stickerMessage ? 'Figurinha' : 'Texto';
      mensagens.push({
        id: itemAtual.key.id,
        telefone: jid.split('@')[0],
        nome,
        texto: (texto || `[${tipo} do histórico · mídia não disponibilizada]`).slice(0, 10000),
        tipo,
        fromMe: itemAtual.key.fromMe === true,
        timestamp: instante,
        ack: Math.max(-1, Math.min(4, Number(itemAtual.status ?? 3) - 1)),
        key: itemAtual.key
      });
      conversas.set(jid, {
        telefone: jid.split('@')[0],
        nome
      });
      if (!evento.cursores[jid] || instante < evento.cursores[jid].timestamp) evento.cursores[jid] = {
        key: itemAtual.key,
        timestamp: instante
      };
    }
    mensagens.sort((primeiro, segundo) => primeiro.timestamp - segundo.timestamp);
    const todos = [...conversas.values()];
    // Primeiro entrega os contatos, depois lotes curtos para não exceder o limite HTTP.
    for (let indice = 0; indice < todos.length; indice += 100) await this.publicar({
      tipo: 'historico',
      canalId: id,
      raw: {
        lote: criptografia.randomUUID(),
        mensagens: [],
        conversas: todos.slice(indice, indice + 100),
        ignoradas: 0
      }
    });
    for (let indice = 0; indice < mensagens.length; indice += 50) await this.publicar({
      tipo: 'historico',
      canalId: id,
      raw: {
        lote: criptografia.randomUUID(),
        mensagens: mensagens.slice(indice, indice + 50),
        conversas: [],
        ignoradas: 0
      }
    });
    if (ignoradas) await this.publicar({
      tipo: 'historico',
      canalId: id,
      raw: {
        lote: criptografia.randomUUID(),
        mensagens: [],
        conversas: [],
        ignoradas
      }
    });
    evento.recebidos += mensagens.length;
    evento.ultimaRecepcao = Date.now();
    evento.progresso = dadosEnviados.progress ?? evento.progresso;
    evento.mensagem = dadosEnviados.isLatest ? 'Lote recebido. O WhatsApp pode disponibilizar outros lotes; use buscar mais histórico.' : 'Recebendo histórico do WhatsApp…';
    await this.salvar(id);
  }
  async erro(id, erro) {
    const evento = await this.carregar(id);
    evento.mensagem = "Falha na importação: " + erro.message;
    await this.salvar(id);
  }
  async status(id) {
    const evento = await this.carregar(id);
    let falhas = 0;
    try {
      for (const nome of await arquivosEmDisco.readdir(caminhos.join(this.diretorio, 'pendencias'))) {
        if (!nome.endsWith('.json')) continue;
        try {
          const paragrafo = JSON.parse(await arquivosEmDisco.readFile(caminhos.join(this.diretorio, 'pendencias', nome), 'utf8'));
          if (paragrafo.tipo === 'historico' && paragrafo.canalId === id) falhas++;
        } catch {}
      }
    } catch (erroAtual) {
      if (erroAtual.code !== 'ENOENT') throw erroAtual;
    }
    return {
      mensagem: evento.mensagem,
      progresso: evento.progresso ?? null,
      falhas,
      pedidos: evento.pedidos,
      recebidos: evento.recebidos,
      ultimaRecepcao: evento.ultimaRecepcao || null
    };
  }
  async pedir(id, cliente) {
    if (!cliente?.user) throw Error('Conecte o canal antes de importar conversas.');
    const evento = await this.carregar(id);
    if (evento.solicitando) return this.status(id);
    if (evento.ultimoPedido && Date.now() - evento.ultimoPedido < 30000) {
      evento.mensagem = 'Solicitação enviada. Aguardando o WhatsApp; tente novamente em alguns segundos.';
      return this.status(id);
    }
    const cursores = Object.values(evento.cursores);
    evento.ultimoPedido = Date.now();
    evento.mensagem = cursores.length ? 'Solicitando mensagens anteriores ao WhatsApp…' : 'Aguardando a sincronização inicial do WhatsApp. Mantenha o celular conectado à internet.';
    await this.salvar(id);
    evento.solicitando = true;
    // Não bloqueia a resposta HTTP durante a busca de vários contatos.
    this.trabalho = (async () => {
      try {
        for (const cursor of cursores) {
          await cliente.fetchMessageHistory(50, cursor.key, cursor.timestamp);
          evento.pedidos++;
        }
        if (cursores.length) evento.mensagem = 'Solicitações enviadas. Aguardando os lotes do WhatsApp.';
      } catch (erroAtual) {
        evento.mensagem = 'Falha ao solicitar histórico: ' + erroAtual.message;
      } finally {
        evento.solicitando = false;
        await this.salvar(id);
      }
    })();
    return this.status(id);
  }
}
module.exports = {
  HistoricoWhatsApp
};
