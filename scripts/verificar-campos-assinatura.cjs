const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
class Elemento {
  constructor(tag) { this.tagName = tag; this.children = []; this.dataset = {}; this.style = {}; this.eventos = {}; this.classList = { toggle() {} }; }
  setAttribute() {}
  append(...nos) { this.children.push(...nos); }
  replaceChildren() { this.children = []; }
  addEventListener(tipo, acao) { this.eventos[tipo] = acao; }
  removeEventListener(tipo) { delete this.eventos[tipo]; }
  focus() {}
  setPointerCapture() {}
  closest(tag) { return this.tagName === tag ? this : null; }
  getBoundingClientRect() { return { width: 600, height: 800 }; }
  querySelector() { return this.children[0]; }
}
const camada = new Elemento('div');
const campo = { id: randomUUID(), documento: 'a', pagina: 2, tipo: 'ASSINATURA', posicaoX: .2, posicaoY: .3, largura: .3, altura: .1 };
const pessoas = [{ id: 'ana', nome: 'Ana', campos: [campo] }];
let erro;
const contexto = vm.createContext({
  document: { createElement: tag => new Elemento(tag) },
  camadaCampos: camada, signatarios: pessoas, documentoAtual: { id: 'a' },
  paginaAtual: 2, totalPaginas: 4, seletorSignatario: { value: 'ana' },
  nomeTipos: { ASSINATURA: 'Assinatura' }, crypto: { randomUUID },
  atualizarSignatarios() {}, mostrarErro: mensagem => { erro = mensagem; },
});
const fonte = fs.readFileSync('src/main/resources/static/js/preparar-assinaturas.js', 'utf8');
const inicio = fonte.indexOf('function desenharCampos()');
const fim = fonte.indexOf('\ndocument\n', inicio);
vm.runInContext(fonte.slice(inicio, fim), contexto);
const renderizar = () => vm.runInContext('desenharCampos()', contexto);
const clicar = texto => {
  const caixa = camada.children[0];
  const controles = caixa.children.find(no => no.className === 'controles-campo');
  controles.children.find(no => no.textContent === texto).eventos.click({ stopPropagation() {} });
};
renderizar();
for (let i = 0; i < 4; i++) clicar('Girar 90°');
assert.equal(campo.rotacao, 0);
assert.ok(Math.abs(campo.largura - .3) < 1e-10);
assert.ok(Math.abs(campo.altura - .1) < 1e-10);
clicar('Girar 90°');
assert.equal(campo.rotacao, 90);
assert.ok(Math.abs(campo.largura - .1 * 800 / 600) < 1e-10);
clicar('Duplicar esse campo em todas as páginas');
assert.equal(pessoas[0].campos.length, 4);
assert.equal(new Set(pessoas[0].campos.map(c => c.id)).size, 4);
assert.deepEqual(pessoas[0].campos.map(c => c.pagina).sort(), [1, 2, 3, 4]);
for (const copia of pessoas[0].campos) {
  for (const prop of ['posicaoX', 'posicaoY', 'largura', 'altura', 'rotacao', 'documento']) assert.equal(copia[prop], campo[prop]);
}
clicar('Duplicar esse campo em todas as páginas');
assert.equal(pessoas[0].campos.length, 4);
const caixa = camada.children[0];
const alca = caixa.children.find(no => no.className === 'alca-redimensionar');
const largura = campo.largura;
caixa.eventos.pointerdown({ target: alca, button: 0, clientX: 0, clientY: 0, pointerId: 1, preventDefault() {} });
caixa.eventos.pointermove({ clientX: 60, clientY: 40 });
assert.ok(Math.abs(campo.largura - largura - .1) < 1e-10);
assert.equal(caixa.style.width, `${campo.largura * 100}%`);
caixa.eventos.pointerup();
contexto.totalPaginas = 100;
clicar('Duplicar esse campo em todas as páginas');
assert.match(erro, /limite de 100/);
assert.equal(pessoas[0].campos.length, 4);
console.log('OK: rotação, duplicação sem sobreposição, geometria, redimensionamento e limite de campos.');
