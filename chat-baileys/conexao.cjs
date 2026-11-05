// O perfil Darwin/Desktop pode encerrar o pareamento antes do QR. O perfil Web
// mantém a busca do histórico sem depender dessa etapa de conexão.
function configuracaoConexao(navegadores) {
  return {
    browser: navegadores.ubuntu('Chrome'),
    syncFullHistory: true,
    shouldSyncHistoryMessage: () => true,
    connectTimeoutMs: 20000
  };
}
function mensagemFalha(codigo) {
  return {
    401: 'Sessão encerrada pelo WhatsApp. Conecte novamente.',
    403: 'O WhatsApp recusou a conexão.',
    405: 'O WhatsApp recusou a versão do protocolo.',
    408: 'O WhatsApp não respondeu no prazo.',
    428: 'O WhatsApp encerrou a conexão antes de concluir o pareamento.',
    440: 'A conexão foi substituída em outro dispositivo.',
    515: 'O WhatsApp pediu para reiniciar a conexão.'
  }[codigo] || 'Não foi possível manter a conexão com o WhatsApp. Tente novamente.';
}
function podeReconectar(codigo, jaConectou, tentativa) {
  return tentativa < 3 && (codigo === 515 || jaConectou && [408, 428, 503].includes(codigo));
}
class PrazoConexao {
  constructor(aoExpirar, agendar = setTimeout, cancelar = clearTimeout) {
    this.agendar = agendar;
    this.limpar = cancelar;
    this.aoExpirar = aoExpirar;
    this.iniciar(45000);
  }
  iniciar(milissegundos) {
    this.cancelar();
    this.timer = this.agendar(this.aoExpirar, milissegundos);
    this.timer?.unref?.();
  }
  qr() {
    this.iniciar(600000);
  }
  cancelar() {
    if (this.timer != null) this.limpar(this.timer);
    this.timer = null;
  }
}
module.exports = {
  configuracaoConexao,
  mensagemFalha,
  podeReconectar,
  PrazoConexao
};
