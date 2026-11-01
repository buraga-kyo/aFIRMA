const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const base=process.env.FIRMA_TEST_BASE_URL||'http://127.0.0.1:18089';
const destino=process.env.FIRMA_TEST_ARTIFACTS||'/tmp/firma-registro-browser';fs.mkdirSync(destino,{recursive:true});
function documentoOriginal(){
 const stream='BT /F1 18 Tf 60 760 Td (CONTEUDO ORIGINAL INALTERADO) Tj ET';
 const objs=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`];
 let pdf='%PDF-1.4\n',offsets=[0];objs.forEach((o,i)=>{offsets.push(Buffer.byteLength(pdf));pdf+=`${i+1} 0 obj\n${o}\nendobj\n`;});const xref=Buffer.byteLength(pdf);pdf+=`xref\n0 6\n0000000000 65535 f \n`;for(const n of offsets.slice(1))pdf+=`${String(n).padStart(10,'0')} 00000 n \n`;pdf+=`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;return Buffer.from(pdf);
}
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1100}});page.setDefaultTimeout(20000);const erros=[];page.on('pageerror',e=>erros.push(e.message));
  await page.goto(base+'/empresa');await page.locator('details').first().locator('summary').click();
  const email='registro-'+Date.now()+'@example.test';await page.fill('#registro-usuario','Horizonte');await page.fill('#registro-email',email);await page.fill('#registro-senha','SenhaTeste2026!');await page.locator('#registro-empresa button').click();await page.waitForFunction(()=>document.querySelector('#mensagem-empresa').textContent.includes('Conta criada'));
  await page.fill('#login-email',email);await page.fill('#login-senha','SenhaTeste2026!');await page.locator('#login-empresa button').click();await page.waitForSelector('#editor-templates:not([hidden])');
  await page.fill('#perfil-nome','Horizonte & Co.');await page.waitForFunction(()=>[...document.querySelectorAll('.registro-thumb img')].length===6&&[...document.querySelectorAll('.registro-thumb img')].every(i=>i.naturalWidth>0));
  await page.locator('.registro-card[data-design=noir]').click();await page.locator('#template-duplicar').click();await page.locator('[data-modelo=nome]').fill('Noir · Horizonte');
  await page.locator('[data-modelo=rodape]').fill('Horizonte & Co. — Compromissos com identidade.');
  await page.locator('.registro-posicoes summary').click();await page.check('#template-editar-posicoes');
  const alca=page.locator('.registro-alca[data-bloco=documento]');const caixa=await alca.boundingBox();await page.mouse.move(caixa.x+caixa.width/2,caixa.y+10);await page.mouse.down();await page.mouse.move(caixa.x+caixa.width/2+7,caixa.y+10);await page.mouse.up();
  await page.locator('#template-ativar').click();await page.locator('#template-salvar').click();await page.waitForFunction(()=>document.querySelector('#mensagem-empresa').textContent.includes('página de assinaturas salvos'));
  const esperado=await page.evaluate(()=>EditorTemplates.dados());assert.equal(esperado.versao,2);assert.equal(esperado.modelos.find(m=>m.id===esperado.ativo).design,'noir');
  await page.reload();await page.waitForSelector('#editor-templates:not([hidden])');assert.deepEqual(await page.evaluate(()=>EditorTemplates.dados()),esperado);
  await page.waitForFunction(()=>document.querySelector('#template-previa').naturalWidth>0);
  await page.locator('#editor-templates').screenshot({path:path.join(destino,'estudio.png')});
  const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#template-baixar').click()]);await download.saveAs(path.join(destino,'amostra-noir.pdf'));
  const token=await page.inputValue('#empresa-token'),auth={Authorization:`Bearer ${token}`};
  const publicoSemLogin=await page.request.post(base+'/api/assineai/empresa/templates/previa',{data:{modelo:esperado.modelos[0],empresa:'Empresa'}});assert.equal(publicoSemLogin.status(),401);
  const invalido=structuredClone(esperado);delete invalido.modelos[0].areas.signatarios;
  const rejeitado=await page.request.put(base+'/api/assineai/empresa',{headers:auth,data:{EmpresaTemplates:invalido}});assert.equal(rejeitado.status(),400);
  const outro='outra-'+Date.now()+'@example.test';await page.request.post(base+'/q9zePu74UYPUaDqUyay4fIjwNV',{data:{username:'Outra empresa',email:outro,password:'SenhaTeste2026!'}});
  const login=await(await page.request.post(base+'/JLv3HEzbcO2uKlg0rsELA66dou',{data:{email:outro,password:'SenhaTeste2026!'}})).json();const empresa2=await(await page.request.get(base+'/api/assineai/empresa',{headers:{Authorization:`Bearer ${login.token}`}})).json();assert.equal(empresa2.EmpresaTemplates,null);
  const pedido={nomeEmpresa:'Horizonte & Co.',nomeAssinatura:'Teste da folha adicional',documentos:[{nome:'Contrato original'}],signatarios:[{nome:'Marina Costa',email:'marina@example.test',formaDeAutenticacao:['tela'],formaDeNotificacao:[],campos:[{documento:0,tipo:'ASSINATURA',pagina:1,posicaoX:.1,posicaoY:.7,largura:.3,altura:.1}]}]};
  const criacao=await page.request.post(base+'/api/assineai/assinaturas',{headers:auth,multipart:{pdfs:{name:'contrato-original.pdf',mimeType:'application/pdf',buffer:documentoOriginal()},dados:{name:'dados.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(pedido))}}});assert.ok(criacao.ok(),await criacao.text());const criado=await criacao.json(),conviteToken=criado.convites[0].token;
  const convite=await browser.newPage({viewport:{width:1100,height:900}});convite.setDefaultTimeout(20000);convite.on('pageerror',e=>erros.push(e.message));await convite.goto(`${base}/web-panels/assinar-documentos/${conviteToken}`);await convite.waitForSelector('#visualizador:not([hidden])');
  await convite.locator('#desenho-assinatura').scrollIntoViewIfNeeded();const desenho=await convite.locator('#desenho-assinatura').boundingBox();await convite.mouse.move(desenho.x+20,desenho.y+40);await convite.mouse.down();await convite.mouse.move(desenho.x+110,desenho.y+80);await convite.mouse.move(desenho.x+160,desenho.y+35);await convite.mouse.up();await convite.check('#aceite');await convite.click('#enviar-assinatura');await convite.waitForSelector('#resultado:not([hidden])');
  const detalhes=await(await page.request.get(`${base}/api/assineai/convites/${conviteToken}`)).json();const id=detalhes.Documentos[0].DocumentoId;
  const pdf=await page.request.get(`${base}/api/assineai/convites/${conviteToken}/documentos/${id}/pdf`);assert.ok(pdf.ok());const arquivo=path.join(destino,'contrato-com-folha-noir.pdf');fs.writeFileSync(arquivo,await pdf.body());
  const texto=require('node:child_process').execFileSync('pdftotext',[arquivo,'-'],{encoding:'utf8'});assert.ok(texto.includes('CONTEUDO ORIGINAL INALTERADO'));assert.ok(texto.includes('Horizonte & Co.'));assert.ok(texto.includes('Marina Costa'));assert.ok(texto.includes('Compromissos com identidade.'));assert.ok(!texto.includes('aFIRMA'));
  // O visual salvo deve estar na folha acrescentada, e a folha deve ser renderizável.
  const ultima=await page.request.get(`${base}/api/assineai/convites/${conviteToken}/documentos/${id}/paginas/2`);assert.ok(ultima.ok());
  const pixel=await page.evaluate(async url=>{const b=await(await fetch(url)).blob(),img=await createImageBitmap(b),c=document.createElement('canvas');c.width=img.width;c.height=img.height;const ctx=c.getContext('2d');ctx.drawImage(img,0,0);return [...ctx.getImageData(0,0,1,1).data].slice(0,3);},`${base}/api/assineai/convites/${conviteToken}/documentos/${id}/paginas/2`);assert.deepEqual(pixel,[23,30,40]);
  fs.writeFileSync(path.join(destino,'folha-real-noir.png'),await ultima.body());
  await page.setViewportSize({width:390,height:844});await page.locator('#editor-templates').screenshot({path:path.join(destino,'estudio-celular.png')});
  assert.deepEqual(erros,[]);console.log('OK: 6 designs, editor de posições, persistência, isolamento, original preservado e folha real com dados da assinatura.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
