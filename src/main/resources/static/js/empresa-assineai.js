const $ = (id) => document.getElementById(id);
function mensagem(texto) {
  $("mensagem-empresa").textContent = texto;
  $("mensagem-empresa").hidden = false;
}
async function pedido(url, dados, metodo = "POST") {
  const resposta = await fetch(url, {
    method: metodo,
    headers: {
      "Content-Type": "application/json",
      ...($("empresa-token").value
        ? { Authorization: `Bearer ${$("empresa-token").value.trim()}` }
        : {}),
    },
    ...(dados ? { body: JSON.stringify(dados) } : {}),
    cache: "no-store",
  });
  if (!resposta.ok) {
    const erro = await resposta.json().catch(() => ({}));
    throw new Error(erro.mensagem || `Operação recusada (${resposta.status}).`);
  }
  return resposta.json();
}
async function carregar() {
  const dados = await pedido("/api/assineai/empresa", null, "GET");
  $("perfil-nome").value = dados.EmpresaNome || "";
  $("perfil-cnpj").value = dados.EmpresaCNPJ || "";
  $("perfil-email").value = dados.EmpresaEmail || "";
  $("perfil-tema").value = String(dados.EmpresaTemaTelaAssinatura || "firma")
    .toLowerCase()
    .includes("caderno")
    ? "Caderno"
    : /solida|sólida/i.test(dados.EmpresaTemaTelaAssinatura || "")
      ? "Cor Solida"
      : "firma";
  $("perfil-empresa").hidden = false;
  localStorage.setItem("assineai-token", $("empresa-token").value.trim());
}
$("login-empresa").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  try {
    const dados = await pedido("/JLv3HEzbcO2uKlg0rsELA66dou", {
      email: $("login-email").value,
      password: $("login-senha").value,
    });
    $("empresa-token").value = dados.token;
    await carregar();
    mensagem("Acesso confirmado.");
  } catch (erro) {
    mensagem(erro.message);
  }
});
$("registro-empresa").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  try {
    await pedido("/q9zePu74UYPUaDqUyay4fIjwNV", {
      username: $("registro-usuario").value,
      email: $("registro-email").value,
      password: $("registro-senha").value,
    });
    mensagem("Conta criada. Entre com e-mail e senha.");
    $("registro-senha").value = "";
  } catch (erro) {
    mensagem(erro.message);
  }
});
$("carregar-empresa").addEventListener("click", () =>
  carregar().catch((erro) => mensagem(erro.message)),
);
$("dados-empresa").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  try {
    await pedido(
      "/api/assineai/empresa",
      {
        EmpresaNome: $("perfil-nome").value,
        EmpresaCNPJ: $("perfil-cnpj").value,
        EmpresaEmail: $("perfil-email").value,
        EmpresaTemaTelaAssinatura: $("perfil-tema").value,
      },
      "PUT",
    );
    mensagem("Dados da empresa salvos.");
  } catch (erro) {
    mensagem(erro.message);
  }
});
$("empresa-token").value = localStorage.getItem("assineai-token") || "";
if ($("empresa-token").value)
  carregar().catch((erro) => mensagem(erro.message));
