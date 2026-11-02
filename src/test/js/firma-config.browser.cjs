// Verifica as telas reais com APIs interceptadas, sem alterar o banco ou conectar o WhatsApp.
const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.FIRMA_TEST_BASE_URL || 'http://127.0.0.1:8080';
(async () => {
  const browser = await chromium.launch({headless:true,executablePath:process.env.CHROME_BIN || '/usr/bin/google-chrome',args:['--no-sandbox']});
  try {
    const leitura = await browser.newPage();
    await leitura.goto(base + '/assineai/trator');
    const token = await leitura.getAttribute('body', 'data-token-teste');
    assert(token, 'Execute no perfil local-test para obter o catálogo por leitura autenticada.');
    const headers = {Authorization:'Bearer ' + token};
    const contextoR = await leitura.request.get(base + '/api/autentica/contexto', {headers}); assert.equal(contextoR.status(),200);
    const contexto = await contextoR.json();
    const modelosR = await leitura.request.get(base + '/api/assineai/empresa/templates', {headers}); assert.equal(modelosR.status(),200);
    const modelos = await modelosR.json();
    await leitura.close();
    for (const width of [1440,500]) {
      const page = await browser.newPage({viewport:{width,height:1000}}), erros = [], escritas = [];
      page.on('pageerror', e => erros.push(e.message));
      let empresa = {EmpresaNome:'Firma de teste',EmpresaCNPJ:'',EmpresaEmail:'firma@example.test',EmpresaTemaTelaAssinatura:'firma',EmpresaTemplates:{versao:2,ativo:modelos[0].id,modelos}};
      let somenteLeitura = false;
      let canais = [{id:801,CanalNome:'Atendimento',CanalNumeroCelular:'5511999999999',CanalProvedorAPI:'Baileys',CanalStatusUso:true,CanalStatus:'DESCONECTADO',CanalVisualizaMenuBOTChat:false},{id:802,CanalNome:'Oficial',CanalProvedorAPI:'Meta',CanalStatusUso:true,CanalMetaNumeroIdentificador:'1234',CanalMetaWAID:'5678'}];
      await page.addInitScript(() => localStorage.setItem('assineai-token','token-simulado'));
      await page.route('**/api/**', async route => {
        const req = route.request(), url = new URL(req.url()), p = url.pathname, method = req.method();
        const json = d => route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(d)});
        if (method !== 'GET') escritas.push({p,method,body:req.postDataJSON()});
        if (p === '/api/assineai/empresa') {
          if (method === 'PUT') empresa = {...empresa,...req.postDataJSON()};
          return json(empresa);
        }
        if (p === '/api/autentica/contexto') return json(somenteLeitura ? {...contexto,modulos:contexto.modulos.map(m=>m.slug==='canais'?{...m,editavel:false,criavel:false,acoes:[]}:m)} : contexto);
        if (p === '/api/assineai/empresa/templates') return json(modelos);
        if (p.endsWith('/templates/previa')) return route.fulfill({status:200,contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=','base64')});
        if (p === '/api/autentica/registros/canais' && method === 'GET') return json({itens:canais,total:canais.length});
        if (p === '/api/autentica/registros/canais' && method === 'POST') {const r={id:803,...req.postDataJSON()};canais.push(r);return json(r);}
        if (/\/registros\/canais\/\d+$/.test(p) && method === 'PUT') {const id=Number(p.split('/').at(-1)),r={...canais.find(c=>c.id===id),...req.postDataJSON()};canais=canais.map(c=>c.id===id?r:c);return json(r);}
        if (p.endsWith('/conectar')) return json({estado:'CONECTANDO'});
        if (p.endsWith('/conexao')) return json({estado:'QR_PENDENTE',qr:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII='});
        throw Error('Chamada não esperada: ' + method + ' ' + p);
      });
      await page.goto(base + '/firma');
      await page.waitForSelector('#firma-canal-form:not([hidden])');
      assert.equal(await page.locator('iframe, #editor-templates, .tabela-scroll').count(),0);
      assert.equal(await page.inputValue('#firma-CanalNome'),'Atendimento');
      assert.equal(await page.locator('#firma-CanalStatus, #firma-CanalNomePastaWhatsApp').count(),0);
      assert(await page.locator('#firma-CanalMetaWAID').isHidden());
      await page.fill('#perfil-nome','Firma editada'); await page.locator('#dados-empresa [type=submit]').click();
      await page.waitForFunction(()=>document.querySelector('#mensagem-empresa').textContent==='Dados daFIRMA salvos.');
      const dados = escritas.find(e=>e.p==='/api/assineai/empresa'); assert(!('EmpresaTemplates' in dados.body),'Salvar dados alterou modelos');
      await page.fill('#firma-CanalNome','Atendimento editado'); await page.check('#firma-CanalVisualizaMenuBOTChat'); await page.click('#firma-canal-salvar');
      await page.waitForFunction(()=>document.querySelector('#mensagem-empresa').textContent==='Canal salvo.');
      assert.equal(canais[0].CanalNome,'Atendimento editado'); assert.equal(canais[0].CanalVisualizaMenuBOTChat,true);
      await page.getByRole('button',{name:'CONECTAR / QR',exact:true}).click(); await page.waitForSelector('#firma-canal-qr:not([hidden])');
      await page.click('#firma-canal-fechar'); assert(await page.locator('#firma-canal-painel').isHidden());
      await page.selectOption('#firma-canal','802'); assert.equal(await page.inputValue('#firma-CanalMetaWAID'),'5678');
      assert.equal(await page.getByRole('button',{name:'CONECTAR / QR',exact:true}).count(),0);
      await page.click('#firma-canal-novo'); await page.fill('#firma-CanalNome','Outro canal'); await page.fill('#firma-CanalNumeroCelular','5511888888888'); await page.click('#firma-canal-salvar');
      await page.waitForFunction(()=>document.querySelector('#firma-canal').value==='803'); assert.equal(canais.length,3);
      fs.mkdirSync('/tmp/firma-config',{recursive:true});await page.screenshot({path:`/tmp/firma-config/firma-${width}.png`,fullPage:true});
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Firma com rolagem horizontal');
      await page.getByText('ACESSO TÉCNICO OU JWT EXISTENTE',{exact:true}).click();await page.fill('#empresa-token','outro-token');assert(await page.locator('#firma-canais').isHidden());
      await page.goto(base + '/advai'); await page.waitForSelector('#editor-templates:not([hidden])');
      assert.equal(await page.inputValue('#perfil-nome'),'Firma editada');
      await page.waitForFunction(()=>document.querySelectorAll('.registro-card').length===6);
      await page.fill('[data-modelo=titulo]','Quem assinou tá aqui'); await page.click('#template-salvar');
      await page.waitForFunction(()=>document.querySelector('#advai-modelos-aviso').textContent==='Modelo salvo.');
      const salvo=escritas.filter(e=>e.p==='/api/assineai/empresa').at(-1);assert.deepEqual(Object.keys(salvo.body),['EmpresaTemplates']);assert.equal(empresa.EmpresaNome,'Firma editada');
      await page.reload();await page.waitForSelector('#editor-templates:not([hidden])'); assert.equal(await page.inputValue('[data-modelo=titulo]'),'Quem assinou tá aqui');
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'ADVai com rolagem horizontal');
      await page.screenshot({path:`/tmp/firma-config/advai-${width}.png`,fullPage:true});
      somenteLeitura = true;await page.goto(base+'/firma');await page.waitForSelector('#firma-canal-form:not([hidden])');assert(await page.locator('#firma-CanalNome').isDisabled());assert(await page.locator('#firma-canal-novo').isHidden());assert(await page.locator('#firma-canal-salvar').isHidden());assert.equal(await page.locator('#firma-canal-acoes button').count(),0);
      assert.deepEqual(erros,[]);await page.close();console.log(`OK [${width}]: firma sem trator, dados, edição/criação de canais, QR fechável, Meta e modelos no ADVai com salvamento separado`);
    }
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
