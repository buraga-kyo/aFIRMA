/* Antes: mvnw -Dtest=NavegacaoTratorTest test. Requer Playwright no NODE_PATH e nostr-tools em scripts/nostr/node_modules (npm ci). */
const { chromium } = require('playwright');
const { finalizeEvent, getPublicKey, verifyEvent } = require(require.resolve('nostr-tools/pure', { paths: [__dirname + '/nostr'] }));
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const raiz = path.resolve(__dirname, '../src/main/resources');
const segredo = new Uint8Array(32).fill(1), outra = new Uint8Array(32).fill(2);
const publica = getPublicKey(segredo);
let respostaExtensao = 'normal', ethereumAtivo = true, publicacaoAtiva = false, pacotePublicado;
const publicacoesRecebidas = [];
let configuracaoSalva;
const pedidos = [];
const servidor = http.createServer(async (req, res) => {
  pedidos.push(req.url);
  if (req.url === '/js/acesso-compartilhado.js') { res.setHeader('Content-Type', 'text/javascript'); res.end(''); return; }
  if (req.url === '/api/assinaturas/politica') { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ carteiraExperimental: ethereumAtivo })); return; }
  if (req.url === '/api/assineai/nostr/estado') { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify({ ativa:publicacaoAtiva,servicoConfigurado:true,chavePublica:getPublicKey(outra),autorizacao:'Autorizacao de teste',referencia:'firma-principal',nome:'Firma Teste',sobre:'' })); return; }
  if (req.url === '/firma-nostr-fixture') {
    const fonte=fs.readFileSync(path.join(raiz,'templates/empresa-assineai.html'),'utf8');
    const a=fonte.indexOf('<section class="etapa" id="cadastro-nostr-empresa">'),b=fonte.indexOf('</section>',a)+10;
    res.setHeader('Content-Type','text/html; charset=utf-8');res.end('<html lang="pt-BR"><head><link rel="stylesheet" href="/css/assinatura.css"></head><body><input id="empresa-token" value="token-teste"><input id="perfil-nome" value="Firma Teste">'+fonte.slice(a,b)+'<script src="/js/nostr-empresa.js"></script></body></html>');return;
  }
  if (req.url === '/api/assineai/nostr/identidades') { res.setHeader('Content-Type','application/json');res.end(JSON.stringify({identidades:[{referencia:'firma-principal',chavePublica:getPublicKey(outra)}]}));return; }
  if (req.url === '/api/assineai/nostr/configuracao') {
    let texto='';for await(const parte of req)texto+=parte;
    configuracaoSalva=JSON.parse(texto);publicacaoAtiva=configuracaoSalva.ativa;
    res.setHeader('Content-Type','application/json');res.end(JSON.stringify({ativa:publicacaoAtiva}));return;
  }
  if (req.url === '/api/assineai/nostr/pausar') {publicacaoAtiva=false;res.writeHead(204);res.end();return;}
  if (req.url === '/api/assineai/nostr/publicacoes' && req.method === 'GET') { res.setHeader('Content-Type','application/json');res.end('[]');return; }
  if (req.url === '/api/assineai/nostr/publicacoes' && req.method === 'POST') {
    let corpo='';for await(const parte of req)corpo+=parte;
    const {evento}=JSON.parse(corpo);publicacoesRecebidas.push(evento);
    const sal='ef'.repeat(32);
    const compromisso=createHash('sha256').update(Buffer.concat([Buffer.from('afirma.nostr.comprovante.v1'+String.fromCharCode(0)),Buffer.from(sal,'hex'),Buffer.from(evento.id,'hex')])).digest('hex');
    const publico=finalizeEvent({kind:1,created_at:1700000000,tags:[['client','aFIRMA']],content:JSON.stringify({protocolo:'afirma.nostr.comprovante.v1',acao:'registrar-aceite',algoritmo:'SHA-256',compromisso})},outra);
    pacotePublicado={formato:'afirma.nostr.publicacao.v1',pedidoPrivado:{aceite:evento,sal},eventoCanonico:JSON.stringify(publico)};
    res.setHeader('Content-Type','application/json');res.end(JSON.stringify({id:'id-publicacao-teste'}));return;
  }
  if (req.url === '/api/assineai/blockchain/preparar') {
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ nomeEmpresa: 'Firma Teste', resumoPdf: '0x' + 'aa'.repeat(32), identificador: 'pedido-teste', identificadorAssinado: '0x' + 'bb'.repeat(32), prazo: 2000000000 })); return;
  }
  if (req.url === '/api/blockchain/concluir') { res.setHeader('Content-Type', 'application/pdf'); res.end('%PDF-1.4\nProva de teste\n%%EOF'); return; }
  const mapas = { '/assineai/inventor': 'templates/inventor.html', '/assinatura': 'templates/assinatura.html', '/afirma/trator': 'templates/documentos-assinatura.html' };
  const relativo = mapas[req.url] || 'static/' + req.url;
  const renderizado = path.resolve(__dirname, '../target/telas-trator-test', path.basename(relativo));
  const arquivo = mapas[req.url] && fs.existsSync(renderizado) ? renderizado : path.resolve(raiz, relativo);
  if ((!arquivo.startsWith(raiz + path.sep) && !arquivo.startsWith(path.resolve(__dirname, '../target/telas-trator-test') + path.sep)) || !fs.existsSync(arquivo)) { res.writeHead(404); res.end(); return; }
  const tipos = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.ttf': 'font/ttf' };
  res.setHeader('Content-Type', tipos[path.extname(arquivo)] || 'text/plain');
  if (req.url === '/assineai/inventor') res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; connect-src 'self'; object-src 'none'");
  res.end(fs.readFileSync(arquivo));
});
(async () => {
  await new Promise(resolve => servidor.listen(0, '127.0.0.1', resolve));
  const navegador = await chromium.launch({ executablePath: process.env.AFIRMA_CHROME || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox'] });
  try {
    const pagina = await navegador.newPage({ viewport: { width: 390, height: 844 }, acceptDownloads: true });
    const erros = [];
    pagina.on('pageerror', e => erros.push(e.message));
    await pagina.exposeFunction('testeChave', () => publica);
    await pagina.exposeFunction('testeAssinar', async evento => {
      const modo = respostaExtensao;
      if (modo === 'demorada') await new Promise(resolve => setTimeout(resolve, 180));
      if (modo === 'recusada') throw Error('Assinatura recusada na extensão.');
      if (modo === 'outro-pedido') {
        const aceite = JSON.parse(evento.content); aceite.desafio = 'ab'.repeat(32); evento.content = JSON.stringify(aceite);
      }
      const assinado = finalizeEvent(evento, modo === 'outra-chave' ? outra : segredo);
      if (modo === 'adulterada') assinado.sig = '00'.repeat(64);
      return JSON.parse(JSON.stringify(assinado));
    });
    await pagina.addInitScript(() => { window.nostr = { getPublicKey: () => window.testeChave(), signEvent: e => window.testeAssinar(e) }; });
    const base = `http://127.0.0.1:${servidor.address().port}`;
    await pagina.goto(base + '/assineai/inventor');
    assert.equal(await pagina.locator('.inventor-navegacao').count(), 0);
    assert.equal(await pagina.locator('#tabela-montagem.af-tabela #painel-inventor-trator #nostr-pdf').count(), 1);
    assert.equal(await pagina.locator('iframe').count(), 0);
    assert.equal(await pagina.locator('[data-trator-menu]').count(),0);
    assert.ok((await pagina.locator('.toolbar-faixa .trator-aba').allTextContents()).includes('Zapiou'));
    const arquivo = { name: 'contrato.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\nContrato original\n%%EOF') };
    const enviar = () => pagina.locator('#nostr-pdf').setInputFiles(arquivo);
    await enviar();
    await pagina.locator('#nostr-conectar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-chave').textContent.includes('Chave conectada'));
    await pagina.locator('#nostr-aceite').check();
    await pagina.locator('#nostr-assinar').click();
    await pagina.locator('#nostr-resultado').waitFor({ state: 'visible' });
    const downloadPromise = pagina.waitForEvent('download');
    await pagina.locator('#nostr-baixar-prova').click();
    const download = await downloadPromise;
    const provaBuffer = fs.readFileSync(await download.path()), prova = JSON.parse(provaBuffer);
    assert.equal(verifyEvent(prova.evento), true);
    assert.equal(prova.evento.pubkey, publica);
    const aceite = JSON.parse(prova.evento.content);
    assert.equal(aceite.acao, 'aceitar-documento');
    assert.equal(aceite.papel, 'Signatário');
    const pdfPromise = pagina.waitForEvent('download');
    await pagina.locator('#nostr-baixar-pdf').click();
    assert.deepEqual(fs.readFileSync(await (await pdfPromise).path()), arquivo.buffer);
    await pagina.locator('#nostr-verificar-pdf').setInputFiles(arquivo);
    const enviarProva = buffer => pagina.locator('#nostr-verificar-prova').setInputFiles({ name: 'prova.json', mimeType: 'application/json', buffer });
    await enviarProva(provaBuffer);
    await pagina.locator('#nostr-chave-esperada').fill(publica);
    await pagina.locator('#nostr-verificar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-verificacao').textContent.includes('criptográfica válida'));
    await pagina.locator('#nostr-verificar-pdf').setInputFiles({ ...arquivo, buffer: Buffer.from('%PDF-1.4\nContrato alterado\n%%EOF') });
    await pagina.locator('#nostr-verificar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-verificacao').textContent.includes('não corresponde'));
    await pagina.locator('#nostr-verificar-pdf').setInputFiles(arquivo);
    await pagina.locator('#nostr-chave-esperada').fill(getPublicKey(outra));
    await pagina.locator('#nostr-verificar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-verificacao').textContent.includes('outra chave'));
    await pagina.locator('#nostr-chave-esperada').fill(publica);
    const adulterada = JSON.parse(provaBuffer); adulterada.evento.sig = '00'.repeat(64);
    await enviarProva(Buffer.from(JSON.stringify(adulterada)));
    await pagina.locator('#nostr-verificar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-verificacao').textContent.includes('inválida'));
    for (const modo of ['outra-chave', 'outro-pedido', 'adulterada', 'recusada']) {
      respostaExtensao = modo;
      await pagina.locator('#nostr-assinar').click();
      await pagina.waitForFunction(() => !document.getElementById('nostr-assinar').disabled);
      assert.equal(await pagina.locator('#nostr-resultado').isVisible(), false, modo);
      assert.equal(await pagina.locator('#nostr-estado').evaluate(n => n.classList.contains('inventor-erro')), true, modo);
    }
    respostaExtensao = 'demorada';
    await pagina.locator('#nostr-assinar').click();
    await pagina.locator('#nostr-papel').fill('Representante');
    await pagina.waitForFunction(() => !document.getElementById('nostr-assinar').disabled);
    assert.equal(await pagina.locator('#nostr-resultado').isVisible(), false);
    respostaExtensao = 'normal';
    await pagina.locator('#nostr-aceite').check();
    await pagina.locator('#nostr-assinar').click();
    await pagina.locator('#nostr-resultado').waitFor({ state: 'visible' });
    // Com autorização da empresa, uma nova prova entra automaticamente na fila.
    publicacaoAtiva = true;
    await pagina.locator('#nostr-assinar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-publicacao-estado').textContent.includes('registrado na fila'));
    assert.equal(publicacoesRecebidas.length,1);
    assert.equal(verifyEvent(publicacoesRecebidas[0]),true);
    assert.equal(publicacoesRecebidas[0].pubkey,publica);
    await enviarProva(Buffer.from(JSON.stringify(pacotePublicado)));
    await pagina.locator('#nostr-chave-firma-esperada').fill(getPublicKey(outra));
    await pagina.locator('#nostr-verificar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-verificacao').textContent.includes('Compromisso público conferido'));
    const salErrado=JSON.parse(JSON.stringify(pacotePublicado));salErrado.pedidoPrivado.sal='ff'.repeat(32);
    await enviarProva(Buffer.from(JSON.stringify(salErrado)));
    await pagina.locator('#nostr-verificar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-verificacao').textContent.includes('não corresponde ao aceite'));
    await pagina.locator('#nostr-chave-firma-esperada').fill('');
    assert.equal(await pagina.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await pagina.screenshot({ path: '/tmp/afirma-inventor-mobile.png', fullPage: true });
    await pagina.locator('[data-inventor="ethereum"]').click();
    assert.equal(await pagina.locator('#area-blockchain').isVisible(), true);
    await pagina.locator('#conectar-carteira').click();
    await pagina.waitForFunction(() => document.getElementById('erro-blockchain').textContent.includes('MetaMask'));
    assert.equal(pedidos.some(url => url.startsWith('/api/assineai/blockchain/preparar')), false);
    // O fluxo Ethereum movido continua funcionando, com carteira e rede simuladas.
    await pagina.evaluate(() => {
      localStorage.setItem('assineai-token', 'token-de-teste');
      window.chamadasEthereum = [];
      window.ethereum = { isMetaMask: true, request: async ({ method }) => {
        chamadasEthereum.push(method);
        if (method === 'eth_requestAccounts' || method === 'eth_accounts') return ['0x' + '11'.repeat(20)];
        if (method === 'eth_chainId') return '0xaa36a7';
        if (method === 'eth_signTypedData_v4') return '0x' + '22'.repeat(65);
        if (method === 'eth_sendTransaction') return '0x' + '33'.repeat(32);
        throw Error('Método inesperado: ' + method);
      } };
    });
    await pagina.locator('#pdf-blockchain').setInputFiles(arquivo);
    await pagina.locator('#assinar-blockchain').click();
    await pagina.locator('#resultado-blockchain').waitFor({ state: 'visible' });
    assert.equal(await pagina.evaluate(() => chamadasEthereum.includes('eth_signTypedData_v4') && chamadasEthereum.includes('eth_sendTransaction')), true);
    assert.equal(await pagina.locator('#assinar-blockchain').isEnabled(), true);
    await pagina.setViewportSize({ width: 1280, height: 900 });
    await pagina.screenshot({ path: '/tmp/afirma-inventor-ethereum.png', fullPage: true });
    ethereumAtivo = false;
    await pagina.reload();
    await pagina.locator('[data-inventor="ethereum"]').click();
    await pagina.waitForFunction(() => document.getElementById('ethereum-politica').textContent.includes('desativadas'));
    assert.equal(await pagina.locator('#area-blockchain').isVisible(), false);
    assert.equal(await pagina.locator('#ethereum-verificar').isVisible(), true);
    await pagina.locator('[data-inventor="nostr"]').click();
    await pagina.evaluate(() => { delete window.nostr; });
    await pagina.locator('#nostr-conectar').click();
    await pagina.waitForFunction(() => document.getElementById('nostr-estado').textContent.includes('NIP-07'));
    await pagina.goto(base+'/firma-nostr-fixture');
    await pagina.waitForFunction(()=>document.querySelectorAll('#firma-nostr-assinador option').length===2);
    await pagina.locator('#firma-nostr-assinador').selectOption('firma-principal');
    await pagina.locator('#firma-nostr-nome').fill('Perfil publico da FIRMA');
    await pagina.locator('#firma-nostr-ativa').check();
    await pagina.locator('#firma-nostr-salvar').click();
    assert.equal(configuracaoSalva,undefined);
    await pagina.locator('#firma-nostr-autorizar').check();
    await pagina.locator('#firma-nostr-salvar').click();
    await pagina.waitForFunction(()=>document.getElementById('firma-nostr-estado').textContent.includes('autorizadas'));
    assert.equal(configuracaoSalva.referencia,'firma-principal');assert.equal(configuracaoSalva.autorizacao,'Autorizacao de teste');
    assert.ok(!Object.keys(configuracaoSalva).some(k=>/nsec|secret|privad|chaveCliente/.test(k)));
    await pagina.locator('#firma-nostr-pausar').click();
    await pagina.waitForFunction(()=>document.getElementById('firma-nostr-estado').textContent.includes('pausadas'));
    assert.equal(publicacaoAtiva,false);
    assert.deepEqual(erros, []);
    const assinaturaHtml = fs.readFileSync(path.join(raiz, 'templates/assinatura.html'), 'utf8');
    assert.equal(assinaturaHtml.includes('/js/blockchain.js'), false);
    assert.equal(assinaturaHtml.includes('id="area-blockchain"'), false);
    const documentosHtml = fs.readFileSync(path.join(raiz, 'templates/documentos-assinatura.html'), 'utf8');
    assert.ok(documentosHtml.includes('id="tabela-telas"'));
    assert.ok(documentosHtml.includes('inventor :: conteudo'));
    console.log('OK: Inventor dentro do painel real do Trator, navegação anterior restaurada, assinatura NIP-07, downloads, integridade do PDF, adulteração, chave divergente, pedido divergente, recusa, alterações durante assinatura, celular, Ethereum simulado, desativação por política e extensão ausente.');
  } finally { await navegador.close(); servidor.close(); }
})().catch(erro => { console.error(erro); servidor.close(); process.exitCode = 1; });
