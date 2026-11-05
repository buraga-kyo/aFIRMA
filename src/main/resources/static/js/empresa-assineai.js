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
  porId("perfil-tema").value = String(dados.EmpresaTemaTelaAssinatura || "firma").toLowerCase().includes("caderno") ? "Caderno" : /solida|sólida/i.test(dados.EmpresaTemaTelaAssinatura || "") ? "Cor Solida" : "firma";
  porId("perfil-empresa").hidden = false;
  await window.FirmaCanais.carregar();
  localStorage.setItem("assineai-token", porId("empresa-token").value.trim());
}
porId("login-empresa").addEventListener("submit", async evento => {
  evento.preventDefault();
  try {
    const dados = await pedido("/JLv3HEzbcO2uKlg0rsELA66dou", {
      email: porId("login-email").value,
      password: porId("login-senha").value
    });
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
      EmpresaTemplates: EditorTemplates.dados(),
      EmpresaNome: porId("perfil-nome").value,
      EmpresaCNPJ: porId("perfil-cnpj").value,
      EmpresaEmail: porId("perfil-email").value,
      EmpresaTemaTelaAssinatura: porId("perfil-tema").value
    }, "PUT");
    mensagem("Dados daFIRMA salvos.");
  } catch (erro) {
    mensagem(erro.message);
  }
});
porId("empresa-token").value = localStorage.getItem("assineai-token") || "";
if (porId("empresa-token").value) carregar().catch(erro => mensagem(erro.message));
