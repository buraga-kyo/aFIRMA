let declaracaoAutorizacaoCertificado = "";
const porId = id => document.getElementById(id);
function mensagem(texto) {
  porId("mensagem-empresa").textContent = texto;
  porId("mensagem-empresa").hidden = false;
}
async function pedido(endereco, dados, metodo = "POST") {
  const resposta = await fetch(endereco, {
    method: metodo,
    headers: {
      "Content-Type": "application/json",
      ...(porId("empresa-token").value ? {
        Authorization: `Bearer ${porId("empresa-token").value.trim()}`
      } : {})
    },
    ...(dados ? {
      body: JSON.stringify(dados)
    } : {}),
    cache: "no-store"
  });
  if (!resposta.ok) {
    const erro = await resposta.json().catch(() => ({}));
    throw new Error(erro.mensagem || `Operação recusada (${resposta.status}).`);
  }
  return resposta.json();
}
async function carregar() {
  const dados = await pedido("/api/assineai/empresa", null, "GET");
  porId("perfil-nome").value = dados.EmpresaNome || "";
  porId("perfil-cnpj").value = dados.EmpresaCNPJ || "";
  porId("perfil-email").value = dados.EmpresaEmail || "";
  for (const [campo, propriedade] of Object.entries(camposResponsavelEmpresa)) {
    porId(campo).value = dados[propriedade] || '';
    porId(campo).dispatchEvent(new Event('input', { bubbles: true }));
  }
  porId('perfil-cnpj').dispatchEvent(new Event('input', { bubbles: true }));
  porId("perfil-tema").value = String(dados.EmpresaTemaTelaAssinatura || "firma").toLowerCase().includes("caderno") ? "Caderno" : /solida|sólida/i.test(dados.EmpresaTemaTelaAssinatura || "") ? "Cor Solida" : "firma";
  const respostaPolitica = await fetch('/api/assinaturas/politica', { cache: 'no-store' });
  if (!respostaPolitica.ok) throw new Error('Não foi possível carregar a autorização de uso do certificado.');
  const politica = await respostaPolitica.json();
  declaracaoAutorizacaoCertificado = politica.declaracaoPoderes;
  porId('texto-autorizacao-automatica').textContent = declaracaoAutorizacaoCertificado;
  porId('empresa-certificado-autorizacao').textContent = declaracaoAutorizacaoCertificado;
  porId('perfil-assinatura-empresa').value = dados.EmpresaAssinaturaAutomatica ? 'automatica' : 'manual';
  porId('autorizar-assinatura-automatica').checked = !!dados.EmpresaAssinaturaAutomatica;
  porId('identificacao-empresa-certificado').textContent = `Empresa nº ${dados.EmpresaId}`;
  atualizarOpcaoAssinaturaEmpresa();
  porId("perfil-empresa").hidden = false;
  porId("login-empresa").closest("section").hidden = true;
  await carregarCertificadoEmpresa();
  await window.FirmaCanais.carregar();

}
porId("login-empresa").addEventListener("submit", async evento => {
  evento.preventDefault();
  try {
    const dados = await pedido("/JLv3HEzbcO2uKlg0rsELA66dou", {
      email: porId("login-email").value,
      password: porId("login-senha").value
    });
    await acessoFirma.conectar("", dados.token);
    porId("empresa-token").value = dados.token;
    await carregar();
    mensagem("Acesso confirmado.");
  } catch (erro) {
    mensagem(erro.message);
  }
});
porId("registro-empresa").addEventListener("submit", async evento => {
  evento.preventDefault();
  try {
    await pedido("/q9zePu74UYPUaDqUyay4fIjwNV", {
      username: porId("registro-usuario").value,
      email: porId("registro-email").value,
      password: porId("registro-senha").value
    });
    mensagem("Conta criada. Entre com e-mail e senha.");
    porId("registro-senha").value = "";
  } catch (erro) {
    mensagem(erro.message);
  }
});
porId("carregar-empresa").addEventListener("click", () => carregar().catch(erro => mensagem(erro.message)));
porId("dados-empresa").addEventListener("submit", async evento => {
  evento.preventDefault();
  try {
    await pedido("/api/assineai/empresa", {
      EmpresaNome: porId("perfil-nome").value,
      EmpresaCNPJ: dadosEmpresaAssinatura.cnpj(porId("perfil-cnpj").value),
      ...Object.fromEntries(Object.entries(camposResponsavelEmpresa).map(([campo, propriedade]) => [propriedade, porId(campo).value])),
      EmpresaEmail: porId("perfil-email").value,
      EmpresaTemaTelaAssinatura: porId("perfil-tema").value,
      EmpresaAssinaturaAutomatica: porId('perfil-assinatura-empresa').value === 'automatica',
      EmpresaAutorizacaoCertificado: porId('autorizar-assinatura-automatica').checked ? declaracaoAutorizacaoCertificado : ''
    }, "PUT");
    mensagem("Dados daFIRMA salvos.");
  } catch (erro) {
    mensagem(erro.message);
  }
});
porId("empresa-token").value = localStorage.getItem("assineai-token") || "";
if (porId("empresa-token").value) carregar().catch(erro => mensagem(erro.message));

document.addEventListener('firma:acesso-conectado', () => carregar().catch(falha => mensagem(falha.message)));

document.addEventListener('firma:desconectado', () => {
  porId('perfil-empresa').hidden = true;
  porId('login-empresa').closest('section').hidden = false;
});

function atualizarOpcaoAssinaturaEmpresa() {
  const automatica = porId('perfil-assinatura-empresa').value === 'automatica';
  porId('grupo-autorizacao-automatica').hidden = !automatica;
  porId('autorizar-assinatura-automatica').required = automatica;
}
porId('perfil-assinatura-empresa').addEventListener('change', atualizarOpcaoAssinaturaEmpresa);

let versaoCertificadoEmpresa = 0;
async function pedidoCertificadoEmpresa(metodo = 'GET', dados) {
  const resposta = await fetch('/api/assineai/empresa/certificado', {
    method: metodo, cache: 'no-store',
    headers: { Authorization: `Bearer ${porId('empresa-token').value.trim()}` },
    ...(dados ? { body: dados } : {})
  });
  if (resposta.status === 204) return null;
  const conteudo = await resposta.json().catch(() => ({}));
  if (!resposta.ok) throw new Error(conteudo.mensagem || 'Não foi possível administrar o certificado desta empresa.');
  return conteudo;
}
function exibirCertificadoEmpresa(dados) {
  const estado = porId('situacao-certificado-empresa');
  estado.textContent = dados.cadastrado
    ? `Titular: ${dados.titular}. Validade: ${new Date(dados.validoAte).toLocaleString('pt-BR')}.${dados.vencido ? ' Certificado vencido; o uso depende da configuração do servidor.' : ''}`
    : 'Nenhum certificado cadastrado nesta tela.';
  porId('remover-certificado-empresa').hidden = !dados.cadastrado;
}
async function carregarCertificadoEmpresa() {
  const versao = ++versaoCertificadoEmpresa;
  try {
    const dados = await pedidoCertificadoEmpresa();
    if (versao === versaoCertificadoEmpresa) exibirCertificadoEmpresa(dados);
  } catch (falha) {
    if (versao === versaoCertificadoEmpresa) porId('situacao-certificado-empresa').textContent = falha.message;
  }
}
function ocultarSenhasEmpresa() {
  for (const botao of document.querySelectorAll('.botao-olho[data-alvo]')) {
    porId(botao.dataset.alvo).type = 'password';
    botao.setAttribute('aria-pressed', 'false');
    botao.setAttribute('aria-label', botao.dataset.alvo === 'empresa-certificado-senha' ? 'Mostrar senha do certificado' : 'Mostrar senha');
  }
}
document.addEventListener('click', evento => {
  const botao = evento.target.closest('.botao-olho[data-alvo]');
  if (!botao) return;
  const campo = porId(botao.dataset.alvo);
  const mostrar = campo.type === 'password';
  const inicio = campo.selectionStart, fim = campo.selectionEnd;
  campo.type = mostrar ? 'text' : 'password';
  botao.setAttribute('aria-pressed', String(mostrar));
  botao.setAttribute('aria-label', `${mostrar ? 'Ocultar' : 'Mostrar'} senha${campo.id === 'empresa-certificado-senha' ? ' do certificado' : ''}`);
  campo.focus({ preventScroll: true });
  if (inicio !== null) campo.setSelectionRange(inicio, fim);
});
porId('form-certificado-empresa').addEventListener('submit', async evento => {
  evento.preventDefault();
  const botao = evento.currentTarget.querySelector('button[type="submit"]');
  if (botao.disabled) return;
  botao.disabled = true;
  try {
    const arquivo = porId('empresa-certificado').files[0];
    if (!arquivo || arquivo.size === 0 || arquivo.size > 2 * 1024 * 1024) throw new Error('Escolha um PFX ou P12 de até 2 MB.');
    if (!porId('empresa-certificado-autorizar').checked || !declaracaoAutorizacaoCertificado) throw new Error('Confirme a autorização para cadastrar o certificado.');
    const dados = new FormData();
    dados.append('assinaturaAutomatica', String(porId('perfil-assinatura-empresa').value === 'automatica'));
    dados.append('certificado', arquivo);
    dados.append('senha', porId('empresa-certificado-senha').value);
    dados.append('declaracaoPoderes', declaracaoAutorizacaoCertificado);
    const versao = ++versaoCertificadoEmpresa;
    const salvo = await pedidoCertificadoEmpresa('POST', dados);
    if (versao !== versaoCertificadoEmpresa) return;
    exibirCertificadoEmpresa(salvo);
    porId('empresa-certificado').value = '';
    porId('empresa-certificado-autorizar').checked = false;
    mensagem(porId('perfil-assinatura-empresa').value === 'automatica' ? 'Certificado e senha salvos com criptografia. A empresa assinará ao criar os convites.' : 'Certificado e senha salvos com criptografia. A assinatura da empresa está no modo manual.');
  } catch (falha) { mensagem(falha.message); }
  finally {
    porId('empresa-certificado-senha').value = '';
    ocultarSenhasEmpresa(); botao.disabled = false;
  }
});
porId('remover-certificado-empresa').addEventListener('click', async evento => {
  const botao = evento.currentTarget;
  if (botao.disabled) return;
  botao.disabled = true;
  try {
    await pedidoCertificadoEmpresa('DELETE');
    await carregar();
    mensagem('Certificado removido. A assinatura automática foi desativada para novos documentos.');
  } catch (falha) { mensagem(falha.message); }
  finally { botao.disabled = false; }
});
function limparCertificadoDaTela() {
  versaoCertificadoEmpresa++;
  porId('empresa-certificado-senha').value = '';
  porId('empresa-certificado').value = '';
  porId('empresa-certificado-autorizar').checked = false;
  ocultarSenhasEmpresa();
}
window.addEventListener('pagehide', limparCertificadoDaTela);
document.addEventListener('firma:desconectado', limparCertificadoDaTela);
document.addEventListener('visibilitychange', () => { if (document.hidden) ocultarSenhasEmpresa(); });

const camposResponsavelEmpresa = {
  'responsavel-nome': 'EmpresaResponsavelNome', 'responsavel-cpf': 'EmpresaResponsavelCPF',
  'responsavel-rg': 'EmpresaResponsavelRG', 'responsavel-email': 'EmpresaResponsavelEmail',
  'responsavel-celular': 'EmpresaResponsavelCelular'
};
for (const [campo, tipo] of [['responsavel-cpf', 'cpf'], ['responsavel-rg', 'rg'], ['responsavel-celular', 'whatsapp']])
  dadosPessoaisSignatario.ligar(porId(campo), tipo, () => {});
dadosEmpresaAssinatura.ligarCnpj(porId('perfil-cnpj'));
