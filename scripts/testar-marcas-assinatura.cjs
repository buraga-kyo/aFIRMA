/* Execute com Playwright disponível no NODE_PATH. Usa apenas arquivos locais e um servidor temporário. */
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const raiz = path.resolve(__dirname, '..', 'src/main/resources');
const tipos = { '.js': 'text/javascript', '.css': 'text/css', '.ttf': 'font/ttf' };
const servidor = http.createServer((req, res) => {
  if (req.url.startsWith('/fixture')) {
    const legado = req.url.includes('legado');
    const html = fs.readFileSync(path.join(raiz, 'templates', legado ? 'convite-legado.html' : 'convite.html'), 'utf8');
    const grupos = ['assinatura', 'rubrica'].map(nome => {
      const inicio = html.lastIndexOf('<div', html.indexOf(`id="grupo-${nome}"`));
      return html.slice(inicio, html.indexOf('</div>', inicio) + 6).replace(/\bhidden(?:="")?/, '');
    }).join('');
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(`<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/css/assinatura.css"><link rel="stylesheet" href="/css/envelopes.css"><link rel="stylesheet" href="/css/marcas-assinatura.css"><body><main style="max-width:800px;margin:auto">${grupos}</main><script src="/js/marcas-assinatura.js"></script><script>window.prontas = {}; for (const nome of ['assinatura', 'rubrica']) FirmaMarcas.preparar('desenho-' + nome, pronta => window.prontas[nome] = pronta);</script></body></html>`);
    return;
  }
  const arquivo = path.resolve(raiz, 'static', '.' + req.url);
  if (!arquivo.startsWith(path.join(raiz, 'static') + path.sep) || !fs.existsSync(arquivo)) { res.writeHead(404); res.end(); return; }
  res.setHeader('Content-Type', tipos[path.extname(arquivo)] || 'application/octet-stream');
  res.end(fs.readFileSync(arquivo));
});
(async () => {
  await new Promise(resolve => servidor.listen(0, '127.0.0.1', resolve));
  const navegador = await chromium.launch({ headless: true, executablePath: process.env.AFIRMA_CHROME || '/usr/bin/google-chrome', args: ['--no-sandbox'] });
  try {
    for (const legado of [false, true]) for (const largura of [390, 1024]) {
      const pagina = await navegador.newPage({ viewport: { width: largura, height: 844 }, hasTouch: true });
      const erros = [];
      pagina.on('pageerror', erro => erros.push(erro.message));
      await pagina.goto(`http://127.0.0.1:${servidor.address().port}/fixture${legado ? '-legado' : ''}`);
      const grupo = pagina.locator('#grupo-assinatura');
      const esperar = (nome, pronta) => pagina.waitForFunction(([nome, pronta]) => window.prontas[nome] === pronta, [nome, pronta]);
      const pixels = () => pagina.evaluate(() => {
        const c = document.getElementById('desenho-assinatura'), p = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
        let transparentes = 0, pretos = 0, azuis = 0, parciais = 0;
        for (let i = 0; i < p.length; i += 4) {
          if (p[i + 3] > 10 && p[i + 3] < 245) parciais++;
          if (p[i + 3] < 10) transparentes++;
          else if (p[i] < 60 && p[i + 1] < 60 && p[i + 2] < 60) pretos++;
          else if (p[i + 2] > 200 && p[i] < 50) azuis++;
        }
        return { largura: c.width, altura: c.height, transparentes, pretos, azuis, parciais, quantidade: p.length / 4 };
      });
      const manual = grupo.locator('.marca-manual');
      const caixa = await manual.boundingBox();
      await pagina.mouse.move(caixa.x + 30, caixa.y + 50);
      await pagina.mouse.down();
      await pagina.mouse.move(caixa.x + 140, caixa.y + 85, { steps: 10 });
      await pagina.mouse.up();
      await esperar('assinatura', true);
      let marca = await pixels();
      assert.ok(marca.largura < 800 && marca.altura < 240 && marca.transparentes > 0);
      await grupo.getByRole('button', { name: 'DIGITAR', exact: true }).click();
      await esperar('assinatura', false);
      await grupo.locator('input[type="text"]').fill('José da Conceição');
      await esperar('assinatura', true);
      assert.ok((await pixels()).transparentes > 0);
      await grupo.locator('select').selectOption('Firma Dancing Script');
      await esperar('assinatura', true);
      await grupo.locator('input[type="text"]').fill('Antônio '.repeat(15).trim());
      await esperar('assinatura', true);
      marca = await pixels();
      assert.ok(marca.largura <= 1200 && marca.altura <= 320);
      await grupo.locator('[data-limpar]').click();
      await esperar('assinatura', false);
      await grupo.getByRole('button', { name: 'ENVIAR IMAGEM', exact: true }).click();
      const png = await pagina.evaluate(() => {
        const c = document.createElement('canvas'); c.width = 400; c.height = 200;
        const ctx = c.getContext('2d'); ctx.fillStyle = 'white'; ctx.fillRect(0, 0, 400, 200);
        ctx.fillStyle = 'black'; ctx.fillRect(50, 50, 50, 100);
        ctx.fillStyle = 'blue'; ctx.fillRect(300, 50, 50, 100);
        ctx.fillStyle = 'rgb(200,200,200)'; ctx.fillRect(150, 80, 50, 40);
        return c.toDataURL().split(',')[1];
      });
      await grupo.locator('input[type="file"]').setInputFiles({ name: 'assinatura.png', mimeType: 'image/png', buffer: Buffer.from(png, 'base64') });
      await esperar('assinatura', true);
      marca = await pixels();
      assert.ok(marca.transparentes > marca.quantidade / 2 && marca.pretos > 0 && marca.azuis > 0);
      const parciais = marca.parciais;
      await grupo.locator('[id$="-intensidade"]').evaluate(input => { input.value = '100'; input.dispatchEvent(new Event('input', { bubbles: true })); });
      assert.ok((await pixels()).parciais > parciais);
      await grupo.locator('[id$="-intensidade"]').evaluate(input => { input.value = '35'; input.dispatchEvent(new Event('input', { bubbles: true })); });
      assert.equal((await pixels()).parciais, parciais);
      await grupo.locator('input[type="checkbox"]').uncheck();
      marca = await pixels();
      assert.ok(marca.transparentes < marca.quantidade / 10);
      await grupo.locator('input[type="checkbox"]').check();
      assert.equal(await grupo.locator('input[type="number"]').count(), 0);
      const area = grupo.locator('.marca-recorte-area');
      const selecao = grupo.locator('.marca-recorte-selecao');
      await area.scrollIntoViewIfNeeded();
      const retangulo = await area.boundingBox();
      const alca = await grupo.locator('[data-alca="w"]').boundingBox();
      await pagina.mouse.move(alca.x + alca.width / 2 + 2, alca.y + alca.height / 2);
      await pagina.mouse.down();
      await pagina.mouse.move(alca.x + alca.width / 2 + retangulo.width * .6, alca.y + alca.height / 2, { steps: 12 });
      await pagina.mouse.up();
      marca = await pixels();
      assert.equal(marca.pretos, 0);
      assert.ok(marca.azuis > 0);
      const antesZoom = await pagina.locator('#desenho-assinatura').evaluate(c => c.toDataURL());
      await grupo.locator('[id$="-zoom"]').evaluate(input => { input.value = '3'; input.dispatchEvent(new Event('input', { bubbles: true })); });
      assert.equal(await pagina.locator('#desenho-assinatura').evaluate(c => c.toDataURL()), antesZoom);
      // O zoom muda apenas a visualização; a seleção mantém as coordenadas na imagem.
      assert.ok((await area.boundingBox()).width > retangulo.width * 2.9);
      const janelaRecorte = grupo.locator('.marca-recorte-janela');
      await janelaRecorte.scrollIntoViewIfNeeded();
      const janelaBox = await janelaRecorte.boundingBox();
      const scrollAntes = await janelaRecorte.evaluate(no => no.scrollLeft);
      await pagina.mouse.move(janelaBox.x + 35, janelaBox.y + janelaBox.height / 2);
      await pagina.mouse.down();
      await pagina.mouse.move(janelaBox.x + 80, janelaBox.y + janelaBox.height / 2, { steps: 5 });
      await pagina.mouse.up();
      assert.ok(await janelaRecorte.evaluate(no => no.scrollLeft) < scrollAntes);
      assert.equal(await pagina.locator('#desenho-assinatura').evaluate(c => c.toDataURL()), antesZoom);
      await grupo.locator('[data-reiniciar-recorte]').click();
      marca = await pixels();
      assert.ok(marca.pretos > 0 && marca.azuis > 0);
      assert.equal(await grupo.locator('[id$="-zoom"]').inputValue(), '1');
      // Arraste de canto com ponteiro touch: preserva os limites e permite nova seleção.
      await area.scrollIntoViewIfNeeded();
      const canto = await grupo.locator('[data-alca="nw"]').boundingBox();
      const toque = await pagina.context().newCDPSession(pagina);
      const x = canto.x + 17, y = canto.y + 17;
      await toque.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] });
      await toque.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: x + 30, y: y + 20 }] });
      await toque.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      await toque.detach();
      const posicao = await selecao.evaluate(c => ({ x: parseFloat(c.style.left), y: parseFloat(c.style.top) }));
      assert.ok(posicao.x > 0 && posicao.y > 0);
      await selecao.focus();
      await pagina.keyboard.press('ArrowLeft');
      const movida = await selecao.evaluate(c => parseFloat(c.style.left));
      assert.ok(movida < posicao.x);
      await grupo.locator('[data-reiniciar-recorte]').click();
      // O mesmo editor acompanha o tamanho da tela sem mudar o PNG selecionado.
      const antesResize = await pagina.locator('#desenho-assinatura').evaluate(c => c.toDataURL());
      await pagina.setViewportSize({ width: largura === 390 ? 430 : 900, height: 844 });
      assert.equal(await pagina.locator('#desenho-assinatura').evaluate(c => c.toDataURL()), antesResize);
      if (!legado) await pagina.screenshot({ path: `/tmp/afirma-recorte-${largura}.png`, fullPage: true });
      // A rubrica tem estado próprio e suporta os mesmos modos.
      const rubrica = pagina.locator('#grupo-rubrica');
      await rubrica.getByRole('button', { name: 'DIGITAR', exact: true }).click();
      await rubrica.locator('input[type="text"]').fill('JC');
      await esperar('rubrica', true);
      await grupo.locator('input[type="file"]').setInputFiles({ name: 'quebrada.png', mimeType: 'image/png', buffer: Buffer.from('invalida') });
      await esperar('assinatura', false);
      assert.equal(await pagina.evaluate(() => prontas.rubrica), true);
      // Respostas lentas de fonte/arquivo não podem sobrescrever um modo mais recente.
      await pagina.evaluate(() => {
        const carregar = document.fonts.load.bind(document.fonts);
        document.fonts.load = (...args) => new Promise(resolve => setTimeout(() => resolve(carregar(...args)), 150));
      });
      await grupo.getByRole('button', { name: 'DIGITAR', exact: true }).click();
      await grupo.locator('input[type="text"]').fill('Outra assinatura');
      await grupo.getByRole('button', { name: 'ENVIAR IMAGEM', exact: true }).click();
      await pagina.waitForTimeout(200);
      assert.equal(await pagina.evaluate(() => prontas.assinatura), false);
      await grupo.getByRole('button', { name: 'DESENHAR', exact: true }).click();
      await esperar('assinatura', true);
      assert.deepEqual(erros, []);
      assert.equal(await pagina.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      if (!legado) await pagina.screenshot({ path: '/tmp/afirma-marcas-mobile.png', fullPage: true });
      console.log(`OK ${legado ? 'legado' : 'atual'} ${largura}px: desenho, duas fontes, nome longo, upload, transparência, alças com mouse/toque, zoom, deslocamento, restauração e teclado.`);
      await pagina.close();
    }
  } finally { await navegador.close(); servidor.close(); }
})().catch(erro => { console.error(erro); servidor.close(); process.exitCode = 1; });
