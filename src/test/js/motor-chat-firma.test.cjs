const {
  test: teste
} = require('node:test');
const assert = require('node:assert/strict');
const arquivosEmDisco = require('node:fs/promises'),
  os = require('node:os'),
  caminhos = require('node:path');
const {
  MotorChatFirma
} = require('../../../chat-baileys/motor-chat-firma.cjs');
const evento = {
  canalId: 'canal-1',
  telefone: '5511999990000',
  identificador: 'mensagem-1',
  texto: 'Olá'
};
teste('mantém uma conversa e entrega cada evento uma vez, inclusive após reinício', async () => {
  const diretorio = await arquivosEmDisco.mkdtemp(caminhos.join(os.tmpdir(), 'firma-motor-'));
  const chamadas = [];
  try {
    const requisitar = async (endereco, opcoes) => {
      chamadas.push({
        url: endereco,
        ...opcoes
      });
      return {
        ok: true,
        json: async () => opcoes.method === 'GET' ? [] : endereco.endsWith('/conversations') ? {
          id: 42
        } : {
          id: 10
        }
      };
    };
    const configurar = () => new MotorChatFirma({
      base: 'http://127.0.0.1:9999',
      caixa: 'caixa-teste',
      segredoIdentidade: 'segredo-de-teste',
      diretorio,
      requisitar
    });
    const motor = configurar();
    await Promise.all([motor.encaminhar(evento), motor.encaminhar({
      ...evento,
      identificador: 'mensagem-2'
    })]);
    await configurar().encaminhar(evento);
    assert.equal(chamadas.filter(item => item.url.endsWith('/conversations') && item.method === 'POST').length, 1);
    assert.equal(chamadas.filter(item => item.url.endsWith('/messages')).length, 2);
    const contato = JSON.parse(chamadas.find(item => item.url.endsWith('/contacts')).body);
    assert.match(contato.identifier_hash, /^[a-f0-9]{64}$/);
  } finally {
    await arquivosEmDisco.rm(diretorio, {
      recursive: true,
      force: true
    });
  }
});
teste('timeout não repete automaticamente a mensagem potencialmente aceita', async () => {
  const diretorio = await arquivosEmDisco.mkdtemp(caminhos.join(os.tmpdir(), 'firma-motor-'));
  let envios = 0;
  try {
    const requisitar = async (endereco, opcoes) => {
      if (endereco.endsWith('/messages')) {
        envios++;
        throw new Error('timeout');
      }
      return {
        ok: true,
        json: async () => opcoes.method === 'GET' ? [{
          id: 9,
          status: 'open'
        }] : {
          id: 9
        }
      };
    };
    const motor = new MotorChatFirma({
      base: 'http://127.0.0.1:9999',
      caixa: 'teste',
      diretorio,
      requisitar
    });
    await assert.rejects(motor.encaminhar(evento), /timeout/);
    await assert.rejects(motor.encaminhar(evento), /sem confirmação/);
    assert.equal(envios, 1);
  } finally {
    await arquivosEmDisco.rm(diretorio, {
      recursive: true,
      force: true
    });
  }
});
