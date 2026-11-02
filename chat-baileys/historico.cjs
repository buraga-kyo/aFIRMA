const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

// A sincronização é fornecida pelo WhatsApp; este módulo não envia mensagens.
class HistoricoWhatsApp {
  constructor({diretorio, publicar, textoMensagem}) {
    this.diretorio = diretorio; this.publicar = publicar; this.textoMensagem = textoMensagem;
    this.canais = new Map();
  }
  async carregar(id) {
    if (!this.canais.has(id)) {
      let estado = {contatos:{}, cursores:{}, recebidos:0, pedidos:0, mensagem:'Aguardando sincronização do WhatsApp.'};
      try { estado = {...estado, ...JSON.parse(await fs.readFile(this.arquivo(id),'utf8'))}; }
      catch(e) { if(e.code !== 'ENOENT') throw e; }
      estado.solicitando=false;this.canais.set(id, estado);
    }
    return this.canais.get(id);
  }
  arquivo(id) { return path.join(this.diretorio,id,'historico.json'); }
  async salvar(id) {
    await fs.mkdir(path.dirname(this.arquivo(id)),{recursive:true,mode:0o700});
    const tmp=this.arquivo(id)+'.'+crypto.randomUUID()+'.tmp';
    await fs.writeFile(tmp,JSON.stringify(await this.carregar(id)),{mode:0o600});
    await fs.rename(tmp,this.arquivo(id));
  }
  async contatos(id, contatos) {
    const e=await this.carregar(id);
    for(const c of contatos || []) {
      const jid=c.jid || c.phoneNumber || c.id;
      if (!/^\d{8,15}@s\.whatsapp\.net$/.test(jid || '')) continue;
      const contato={jid,nome:c.name || c.notify || c.verifiedName || ''};
      e.contatos[c.id]=contato;if(c.lid)e.contatos[c.lid]=contato;e.contatos[jid]=contato;
    }
    await this.salvar(id);
  }
  async receber(id, payload) {
    await this.contatos(id,payload.contacts);
    const e=await this.carregar(id), mensagens=[], conversas=new Map();let ignoradas=0;
    for(const c of payload.chats || []) {
      const jid=e.contatos[c.id]?.jid || c.id;
      if(!/^\d{8,15}@s\.whatsapp\.net$/.test(jid || '')) {ignoradas++;continue;}
      conversas.set(jid,{telefone:jid.split('@')[0],nome:e.contatos[jid]?.nome || c.name || ''});
    }
    for(const m of payload.messages || []) {
      const jid=e.contatos[m.key?.remoteJid]?.jid || m.key?.remoteJid;
      const timestamp=Number(m.messageTimestamp?.toString() || 0);
      const corpo=m.message?.ephemeralMessage?.message || m.message?.viewOnceMessage?.message || m.message || {};
      if(!/^\d{8,15}@s\.whatsapp\.net$/.test(jid || '') || !m.key?.id || !timestamp || corpo.protocolMessage || corpo.reactionMessage) {ignoradas++;continue;}
      const midia=corpo.imageMessage || corpo.videoMessage || corpo.audioMessage || corpo.documentMessage || corpo.stickerMessage;
      const texto=this.textoMensagem({...m,message:corpo});
      if(!texto&&!midia){ignoradas++;continue;}
      const nome=e.contatos[jid]?.nome || (!m.key.fromMe ? m.pushName : '') || '';
      const tipo=corpo.imageMessage?'Imagem':corpo.videoMessage?'Vídeo':corpo.audioMessage?'Áudio':corpo.documentMessage?'Documento':corpo.stickerMessage?'Figurinha':'Texto';
      mensagens.push({id:m.key.id,telefone:jid.split('@')[0],nome,texto:(texto || `[${tipo} do histórico · mídia não disponibilizada]`).slice(0,10000),tipo,fromMe:m.key.fromMe===true,timestamp,ack:Math.max(-1,Math.min(4,Number(m.status ?? 3)-1)),key:m.key});
      conversas.set(jid,{telefone:jid.split('@')[0],nome});
      if(!e.cursores[jid] || timestamp < e.cursores[jid].timestamp)e.cursores[jid]={key:m.key,timestamp};
    }
    mensagens.sort((a,b)=>a.timestamp-b.timestamp);
    const todos=[...conversas.values()];
    // Primeiro entrega os contatos, depois lotes curtos para não exceder o limite HTTP.
    for(let i=0;i<todos.length;i+=100)await this.publicar({tipo:'historico',canalId:id,raw:{lote:crypto.randomUUID(),mensagens:[],conversas:todos.slice(i,i+100),ignoradas:0}});
    for(let i=0;i<mensagens.length;i+=50)await this.publicar({tipo:'historico',canalId:id,raw:{lote:crypto.randomUUID(),mensagens:mensagens.slice(i,i+50),conversas:[],ignoradas:0}});
    if(ignoradas)await this.publicar({tipo:'historico',canalId:id,raw:{lote:crypto.randomUUID(),mensagens:[],conversas:[],ignoradas}});
    e.recebidos+=mensagens.length;e.ultimaRecepcao=Date.now();
    e.progresso=payload.progress ?? e.progresso;
    e.mensagem=payload.isLatest?'Lote recebido. O WhatsApp pode disponibilizar outros lotes; use buscar mais histórico.':'Recebendo histórico do WhatsApp…';
    await this.salvar(id);
  }
  async erro(id,erro) {const e=await this.carregar(id);e.mensagem="Falha na importação: "+erro.message;await this.salvar(id);}
  async status(id) {
    const e=await this.carregar(id);let falhas=0;
    try {for(const nome of await fs.readdir(path.join(this.diretorio,'pendencias'))){if(!nome.endsWith('.json'))continue;try{const p=JSON.parse(await fs.readFile(path.join(this.diretorio,'pendencias',nome),'utf8'));if(p.tipo==='historico'&&p.canalId===id)falhas++;}catch{}}}catch(err){if(err.code!=='ENOENT')throw err;}
    return {mensagem:e.mensagem,progresso:e.progresso ?? null,falhas,pedidos:e.pedidos,recebidos:e.recebidos,ultimaRecepcao:e.ultimaRecepcao || null};
  }
  async pedir(id, cliente) {
    if(!cliente?.user)throw Error('Conecte o canal antes de importar conversas.');
    const e=await this.carregar(id);
    if(e.solicitando)return this.status(id);
    if(e.ultimoPedido && Date.now()-e.ultimoPedido<30000){e.mensagem='Solicitação enviada. Aguardando o WhatsApp; tente novamente em alguns segundos.';return this.status(id);}
    const cursores=Object.values(e.cursores);e.ultimoPedido=Date.now();e.mensagem=cursores.length?'Solicitando mensagens anteriores ao WhatsApp…':'Aguardando a sincronização inicial do WhatsApp. Mantenha o celular conectado à internet.';
    await this.salvar(id);e.solicitando=true;
    // Não bloqueia a resposta HTTP durante a busca de vários contatos.
    this.trabalho=(async()=>{try{for(const cursor of cursores){await cliente.fetchMessageHistory(50,cursor.key,cursor.timestamp);e.pedidos++;}if(cursores.length)e.mensagem='Solicitações enviadas. Aguardando os lotes do WhatsApp.';}catch(err){e.mensagem='Falha ao solicitar histórico: '+err.message;}finally{e.solicitando=false;await this.salvar(id);}})();
    return this.status(id);
  }
}
module.exports={HistoricoWhatsApp};
