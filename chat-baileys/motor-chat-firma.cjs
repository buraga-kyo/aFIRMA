const arquivosEmDisco = require('node:fs/promises');
const caminhos = require('node:path');
const criptografia = require('node:crypto');

// A ponte só chama este motor depois que o Java aceita o evento do atendimento.
class MotorChatFirma {
  constructor({
    base,
    caixa,
    segredoIdentidade = '',
    diretorio,
    requisitar = fetch
  }) {
    this.base = base.replace(/\/$/, '');
    this.caixa = caixa;
    this.segredoIdentidade = segredoIdentidade;
    this.diretorio = diretorio;
    this.requisitar = requisitar;
    this.filas = new Map();
  }
  async chamar(caminho, metodo, dados) {
    const resposta = await this.requisitar(`${this.base}/public/api/v1/inboxes/${encodeURIComponent(this.caixa)}${caminho}`, {
      method: metodo,
      headers: {
        'Content-Type': 'application/json'
      },
      ...(dados ? {
        body: JSON.stringify(dados)
      } : {}),
      signal: AbortSignal.timeout(10000)
    });
    if (!resposta.ok) throw new Error(`Motor de chat respondeu ${resposta.status}`);
    return resposta.json();
  }
  async ler(nome) {
    try {
      return JSON.parse(await arquivosEmDisco.readFile(caminhos.join(this.diretorio, nome), 'utf8'));
    } catch (erro) {
      if (erro.code === 'ENOENT') return null;
      throw erro;
    }
  }
  async gravar(nome, dados) {
    await arquivosEmDisco.mkdir(this.diretorio, {
      recursive: true,
      mode: 0o700
    });
    const destino = caminhos.join(this.diretorio, nome),
      temporario = `${destino}.${criptografia.randomUUID()}.tmp`;
    await arquivosEmDisco.writeFile(temporario, JSON.stringify(dados), {
      mode: 0o600
    });
    await arquivosEmDisco.rename(temporario, destino);
  }
  async encaminhar(evento) {
    if (!evento.canalId || !/^\d{8,15}$/.test(evento.telefone) || !evento.identificador) throw new Error('Evento sem identificação daFIRMA');
    const chave = criptografia.createHash('sha256').update(`${evento.canalId}:${evento.telefone}`).digest('hex');
    const anterior = this.filas.get(chave) || Promise.resolve();
    const atual = anterior.catch(() => {}).then(() => this.entregar(evento, chave));
    this.filas.set(chave, atual);
    try {
      await atual;
    } finally {
      if (this.filas.get(chave) === atual) this.filas.delete(chave);
    }
  }
  async entregar(evento, chave) {
    const identificador = `${evento.canalId}-${evento.telefone}`;
    const recibo = criptografia.createHash('sha256').update(`${evento.canalId}:${evento.identificador}`).digest('hex') + '.json';
    const anterior = await this.ler(recibo);
    if (anterior?.estado === 'ENVIADO') return;
    if (anterior) throw new Error('Entrega ao motor de chat sem confirmação; confira o serviço antes de repetir');
    const contato = {
      source_id: identificador,
      identifier: identificador,
      name: evento.nome || evento.telefone,
      phone_number: `+${evento.telefone}`
    };
    if (this.segredoIdentidade) contato.identifier_hash = criptografia.createHmac('sha256', this.segredoIdentidade).update(identificador).digest('hex');
    await this.chamar('/contacts', 'POST', contato);
    const caminho = `/contacts/${encodeURIComponent(identificador)}/conversations`;
    let conversa = await this.ler(`${chave}.conversa.json`);
    if (!conversa) {
      const conversas = await this.chamar(caminho, 'GET');
      conversa = conversas.filter(item => item.status !== 'resolved').sort((primeiro, segundo) => segundo.id - primeiro.id)[0];
      if (!conversa) conversa = await this.chamar(caminho, 'POST', {});
      if (!Number.isSafeInteger(conversa.id) || conversa.id < 1) throw new Error('Conversa inválida no motor de chat');
      await this.gravar(`${chave}.conversa.json`, {
        id: conversa.id
      });
    }
    // Grava antes de enviar. Se a conexão cair, dá para conferir o envio sem mandar a mensagem duas vezes.
    await this.gravar(recibo, {
      estado: 'ENVIANDO',
      conversa: conversa.id
    });
    await this.chamar(`${caminho}/${conversa.id}/messages`, 'POST', {
      content: evento.texto || '[Anexo recebido]',
      echo_id: `${evento.canalId}-${evento.identificador}`
    });
    await this.gravar(recibo, {
      estado: 'ENVIADO',
      conversa: conversa.id
    });
  }
}
module.exports = {
  MotorChatFirma
};
