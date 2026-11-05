const {
    test: teste
  } = require('node:test'),
  assert = require('node:assert/strict');
const {
  configuracaoConexao,
  mensagemFalha,
  podeReconectar,
  PrazoConexao
} = require('../../../chat-baileys/conexao.cjs');
teste('pareamento usa o perfil Web mantendo sincronização habilitada', () => {
  const item = configuracaoConexao({
    ubuntu: botao => ['Ubuntu', botao, 'teste']
  });
  assert.deepEqual(item.browser, ['Ubuntu', 'Chrome', 'teste']);
  assert.equal(item.syncFullHistory, true);
  assert.equal(item.shouldSyncHistoryMessage(), true);
  assert.equal(item.connectTimeoutMs, 20000);
});
teste('não repete indefinidamente falhas antes do QR nem rejeições do WhatsApp', () => {
  for (const codigo of [401, 403, 405, 408, 428, 440, 503, undefined]) assert.equal(podeReconectar(codigo, false, 0), false);
  assert.equal(podeReconectar(515, false, 0), true);
  assert.equal(podeReconectar(428, true, 0), true);
  assert.equal(podeReconectar(515, false, 3), false);
  assert.equal(podeReconectar(428, true, 3), false);
  assert.match(mensagemFalha(428), /encerrou a conexão/);
});
teste('prazo inicial é finito e a chegada do QR troca para o prazo de leitura', () => {
  let proximo = 0,
    callback;
  const limpos = [],
    agendados = [];
  const paragrafo = new PrazoConexao(() => callback = 'expirou', (campo, milissegundos) => {
    agendados.push({
      f: campo,
      ms: milissegundos
    });
    return ++proximo;
  }, itemAtual => limpos.push(itemAtual));
  assert.equal(agendados[0].ms, 45000);
  paragrafo.qr();
  assert.deepEqual(limpos, [1]);
  assert.equal(agendados[1].ms, 600000);
  agendados[1].f();
  assert.equal(callback, 'expirou');
  paragrafo.cancelar();
  assert.deepEqual(limpos, [1, 2]);
});
