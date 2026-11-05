const {
  chromium
} = require('playwright');
const arquivosEmDisco = require('node:fs'),
  caminhos = require('node:path'),
  assert = require('node:assert/strict');
const base = process.env.FIRMA_TEST_BASE_URL || 'http://127.0.0.1:18089';
const destino = process.env.FIRMA_TEST_ARTIFACTS || '/tmp/firma-registro-browser';
arquivosEmDisco.mkdirSync(destino, {
  recursive: true
});
function documentoOriginal() {
  const stream = 'BT /F1 18 Tf 60 760 Td (CONTEUDO ORIGINAL INALTERADO) Tj ET';
  const objs = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>', '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>', '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`];
  let pdf = '%PDF-1.4\n',
    offsets = [0];
  objs.forEach((opcao, indice) => {
    offsets.push(Buffer.byteLength(pdf));
    pdf += `${indice + 1} 0 obj\n${opcao}\nendobj\n`;
  });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 6\n0000000000 65535 f \n`;
  for (const valor of offsets.slice(1)) pdf += `${String(valor).padStart(10, '0')} 00000 n \n`;
  pdf += `trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return Buffer.from(pdf);
}
(async () => {
  const navegador = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome',
    args: ['--no-sandbox']
  });
  try {
    const paginaAtual = await navegador.newPage({
      viewport: {
        width: 1440,
        height: 1100
      }
    });
    paginaAtual.setDefaultTimeout(20000);
    const erros = [];
    paginaAtual.on('pageerror', evento => erros.push(evento.message));
    await paginaAtual.goto(base + '/empresa');
    await paginaAtual.locator('details').first().locator('summary').click();
    const email = 'registro-' + Date.now() + '@example.test';
    await paginaAtual.fill('#registro-usuario', 'Horizonte');
    await paginaAtual.fill('#registro-email', email);
    await paginaAtual.fill('#registro-senha', 'SenhaTeste2026!');
    await paginaAtual.locator('#registro-empresa button').click();
    await paginaAtual.waitForFunction(() => document.querySelector('#mensagem-empresa').textContent.includes('Conta criada'));
    await paginaAtual.fill('#login-email', email);
    await paginaAtual.fill('#login-senha', 'SenhaTeste2026!');
    await paginaAtual.locator('#login-empresa button').click();
    await paginaAtual.waitForSelector('#perfil-empresa:not([hidden])');
    await paginaAtual.fill('#perfil-nome', 'Horizonte & Co.');
    await paginaAtual.locator('#dados-empresa [type=submit]').click();
    await paginaAtual.waitForFunction(() => document.querySelector('#mensagem-empresa').textContent.includes('Dados daFIRMA salvos'));
    await paginaAtual.goto(base + '/advai');
    await paginaAtual.waitForSelector('#editor-templates:not([hidden])');
    await paginaAtual.waitForFunction(() => [...document.querySelectorAll('.registro-thumb img')].length === 6 && [...document.querySelectorAll('.registro-thumb img')].every(indice => indice.naturalWidth > 0));
    await paginaAtual.locator('.registro-card[data-design=noir]').click();
    await paginaAtual.locator('#template-duplicar').click();
    await paginaAtual.locator('[data-modelo=nome]').fill('Noir · Horizonte');
    await paginaAtual.locator('[data-modelo=rodape]').fill('Horizonte & Co.: documento assinado.');
    await paginaAtual.locator('.registro-posicoes summary').click();
    await paginaAtual.check('#template-editar-posicoes');
    const alca = paginaAtual.locator('.registro-alca[data-bloco=documento]');
    const caixa = await alca.boundingBox();
    await paginaAtual.mouse.move(caixa.x + caixa.width / 2, caixa.y + 10);
    await paginaAtual.mouse.down();
    await paginaAtual.mouse.move(caixa.x + caixa.width / 2 + 7, caixa.y + 10);
    await paginaAtual.mouse.up();
    await paginaAtual.locator('#template-ativar').click();
    await paginaAtual.locator('#template-salvar').click();
    await paginaAtual.waitForFunction(() => document.querySelector('#advai-modelos-aviso').textContent.includes('Modelo salvo'));
    const esperado = await paginaAtual.evaluate(() => EditorTemplates.dados());
    assert.equal(esperado.versao, 2);
    assert.equal(esperado.modelos.find(item => item.id === esperado.ativo).design, 'noir');
    await paginaAtual.reload();
    await paginaAtual.waitForSelector('#editor-templates:not([hidden])');
    assert.deepEqual(await paginaAtual.evaluate(() => EditorTemplates.dados()), esperado);
    await paginaAtual.waitForFunction(() => document.querySelector('#template-previa').naturalWidth > 0);
    await paginaAtual.locator('#editor-templates').screenshot({
      path: caminhos.join(destino, 'estudio.png')
    });
    const [download] = await Promise.all([paginaAtual.waitForEvent('download'), paginaAtual.locator('#template-baixar').click()]);
    await download.saveAs(caminhos.join(destino, 'amostra-noir.pdf'));
    const token = await paginaAtual.inputValue('#empresa-token'),
      credenciais = {
        Authorization: `Bearer ${token}`
      };
    const publicoSemLogin = await paginaAtual.request.post(base + '/api/assineai/empresa/templates/previa', {
      data: {
        modelo: esperado.modelos[0],
        empresa: 'Empresa'
      }
    });
    assert.equal(publicoSemLogin.status(), 401);
    const invalido = structuredClone(esperado);
    delete invalido.modelos[0].areas.signatarios;
    const rejeitado = await paginaAtual.request.put(base + '/api/assineai/empresa', {
      headers: credenciais,
      data: {
        EmpresaTemplates: invalido
      }
    });
    assert.equal(rejeitado.status(), 400);
    const outro = 'outra-' + Date.now() + '@example.test';
    await paginaAtual.request.post(base + '/q9zePu74UYPUaDqUyay4fIjwNV', {
      data: {
        username: 'Outra empresa',
        email: outro,
        password: 'SenhaTeste2026!'
      }
    });
    const acesso = await (await paginaAtual.request.post(base + '/JLv3HEzbcO2uKlg0rsELA66dou', {
      data: {
        email: outro,
        password: 'SenhaTeste2026!'
      }
    })).json();
    const empresa2 = await (await paginaAtual.request.get(base + '/api/assineai/empresa', {
      headers: {
        Authorization: `Bearer ${acesso.token}`
      }
    })).json();
    assert.equal(empresa2.EmpresaTemplates, null);
    const pedido = {
      nomeEmpresa: 'Horizonte & Co.',
      nomeAssinatura: 'Teste da folha adicional',
      documentos: [{
        nome: 'Contrato original'
      }],
      signatarios: [{
        nome: 'Marina Costa',
        email: 'marina@example.test',
        formaDeAutenticacao: ['tela'],
        formaDeNotificacao: [],
        campos: [{
          documento: 0,
          tipo: 'ASSINATURA',
          pagina: 1,
          posicaoX: .1,
          posicaoY: .7,
          largura: .3,
          altura: .1
        }]
      }]
    };
    const criacao = await paginaAtual.request.post(base + '/api/assineai/assinaturas', {
      headers: credenciais,
      multipart: {
        pdfs: {
          name: 'contrato-original.pdf',
          mimeType: 'application/pdf',
          buffer: documentoOriginal()
        },
        dados: {
          name: 'dados.json',
          mimeType: 'application/json',
          buffer: Buffer.from(JSON.stringify(pedido))
        }
      }
    });
    assert.ok(criacao.ok(), await criacao.text());
    const criado = await criacao.json(),
      conviteToken = criado.convites[0].token;
    const convite = await navegador.newPage({
      viewport: {
        width: 1100,
        height: 900
      }
    });
    convite.setDefaultTimeout(20000);
    convite.on('pageerror', evento => erros.push(evento.message));
    await convite.goto(`${base}/web-panels/assinar-documentos/${conviteToken}`);
    await convite.waitForSelector('#visualizador:not([hidden])');
    await convite.locator('#desenho-assinatura').scrollIntoViewIfNeeded();
    const desenho = await convite.locator('#desenho-assinatura').boundingBox();
    await convite.mouse.move(desenho.x + 20, desenho.y + 40);
    await convite.mouse.down();
    await convite.mouse.move(desenho.x + 110, desenho.y + 80);
    await convite.mouse.move(desenho.x + 160, desenho.y + 35);
    await convite.mouse.up();
    await convite.check('#aceite');
    await convite.click('#enviar-assinatura');
    await convite.waitForSelector('#resultado:not([hidden])');
    const detalhes = await (await paginaAtual.request.get(`${base}/api/assineai/convites/${conviteToken}`)).json();
    const id = detalhes.Documentos[0].DocumentoId;
    const pdf = await paginaAtual.request.get(`${base}/api/assineai/convites/${conviteToken}/documentos/${id}/pdf`);
    assert.ok(pdf.ok());
    const arquivo = caminhos.join(destino, 'contrato-com-folha-noir.pdf');
    arquivosEmDisco.writeFileSync(arquivo, await pdf.body());
    const texto = require('node:child_process').execFileSync('pdftotext', [arquivo, '-'], {
      encoding: 'utf8'
    });
    assert.ok(texto.includes('CONTEUDO ORIGINAL INALTERADO'));
    assert.ok(texto.includes('Horizonte & Co.'));
    assert.ok(texto.includes('Marina Costa'));
    assert.ok(texto.includes('Compromissos com identidade.'));
    assert.ok(!texto.includes('aFIRMA'));
    // O visual salvo deve estar na folha acrescentada, e a folha deve ser renderizável.
    const ultima = await paginaAtual.request.get(`${base}/api/assineai/convites/${conviteToken}/documentos/${id}/paginas/2`);
    assert.ok(ultima.ok());
    const pixel = await paginaAtual.evaluate(async endereco => {
      const resposta = await (await fetch(endereco)).blob(),
        imagem = await createImageBitmap(resposta),
        elemento = document.createElement('canvas');
      elemento.width = imagem.width;
      elemento.height = imagem.height;
      const ctx = elemento.getContext('2d');
      ctx.drawImage(imagem, 0, 0);
      return [...ctx.getImageData(0, 0, 1, 1).data].slice(0, 3);
    }, `${base}/api/assineai/convites/${conviteToken}/documentos/${id}/paginas/2`);
    assert.deepEqual(pixel, [23, 30, 40]);
    arquivosEmDisco.writeFileSync(caminhos.join(destino, 'folha-real-noir.png'), await ultima.body());
    await paginaAtual.setViewportSize({
      width: 390,
      height: 844
    });
    await paginaAtual.locator('#editor-templates').screenshot({
      path: caminhos.join(destino, 'estudio-celular.png')
    });
    assert.deepEqual(erros, []);
    console.log('OK: 6 designs, editor de posições, persistência, isolamento, original preservado e folha real com dados da assinatura.');
  } finally {
    await navegador.close();
  }
})().catch(evento => {
  console.error(evento);
  process.exit(1);
});
