const {chromium,request}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const base=process.env.FIRMA_TEST_BASE_URL||'http://127.0.0.1:18092';
const destino=process.env.FIRMA_TEST_ARTIFACTS||'/tmp/firma-editor-nativo';
fs.mkdirSync(destino,{recursive:true});
function pdfOriginal(){
 const texto='BT /F1 18 Tf 60 760 Td (CONTRATO ORIGINAL DA FIRMA) Tj ET';
 const objetos=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',`<< /Length ${texto.length} >>\nstream\n${texto}\nendstream`];
 let pdf='%PDF-1.4\n',offsets=[0];objetos.forEach((obj,i)=>{offsets.push(Buffer.byteLength(pdf));pdf+=`${i+1} 0 obj\n${obj}\nendobj\n`;});
 const xref=Buffer.byteLength(pdf);pdf+=`xref\n0 6\n0000000000 65535 f \n`;for(const offset of offsets.slice(1))pdf+=`${String(offset).padStart(10,'0')} 00000 n \n`;
 return Buffer.from(pdf+`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`);
}
(async()=>{
 const api=await request.newContext({baseURL:base});
 for(let i=0;i<40;i++){try{const resposta=await api.get('/assineai/pagina-assinatura');if(resposta.ok())break;}catch{}await new Promise(r=>setTimeout(r,1000));} 
 async function conta(sufixo){
  const email=`editor-${Date.now()}-${sufixo}@example.test`,password='SenhaTeste2026!';
  const registro=await api.post('/q9zePu74UYPUaDqUyay4fIjwNV',{data:{username:'Editor Teste',email,password}});assert.equal(registro.status(),201,await registro.text());
  const login=await api.post('/JLv3HEzbcO2uKlg0rsELA66dou',{data:{email,password}});assert(login.ok());return(await login.json()).token;
 }
 const cache='/tmp/firma-editor-nativo-contas.json',tokens=fs.existsSync(cache)?JSON.parse(fs.readFileSync(cache)):null;
 const token=tokens?.[0]||await conta('a'),outro=tokens?.[1]||await conta('b');
 if(!tokens)fs.writeFileSync(cache,JSON.stringify([token,outro]),{mode:0o600});
 const headers={Authorization:`Bearer ${token}`};
 const navegador=await chromium.launch({headless:true,executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',args:['--no-sandbox']});
 try{
  const contexto=await navegador.newContext({viewport:{width:1500,height:1100}}),pagina=await contexto.newPage(),erros=[];
  pagina.on('pageerror',e=>erros.push(e.message));pagina.setDefaultTimeout(20000);
  await contexto.addInitScript(t=>localStorage.setItem('assineai-token',t),token);
  await pagina.goto(base+'/assineai/pagina-assinatura');await pagina.waitForSelector('#editor-templates:not([hidden])');
  await pagina.setInputFiles('#studio-pdf',{name:'contrato.pdf',mimeType:'application/pdf',buffer:pdfOriginal()});
  await pagina.waitForFunction(()=>document.querySelector('#studio-status').textContent.includes('Arraste'));
  for(const tipo of ['text','date','checkbox','signature','file'])await pagina.locator(`[data-campo-tipo=${tipo}]`).click();
  assert.equal(await pagina.locator('.studio-campo').count(),5);
  await pagina.fill('#studio-modelo-nome','Contrato nativo v1');await pagina.locator('#studio-salvar').click();
  await pagina.waitForFunction(()=>document.querySelector('#studio-status').textContent.includes('Versão 1.'));
  await pagina.fill('#studio-modelo-nome','Contrato nativo v2');await pagina.locator('#studio-salvar').click();
  await pagina.waitForFunction(()=>document.querySelector('#studio-status').textContent.includes('Versão 2.'));
  const biblioteca=await(await api.get('/api/assineai/editor/modelos',{headers})).json(),modelo=biblioteca[0];assert.equal(modelo.versao,2);
  const proibido=await api.get(`/api/assineai/editor/modelos/${modelo.id}`,{headers:{Authorization:`Bearer ${outro}`}});assert.equal(proibido.status(),404);
  assert.equal((await api.get('/api/assineai/editor/modelos')).status(),401);
  const conflito=await api.post('/api/assineai/editor/modelos',{headers,multipart:{pdf:{name:'original.pdf',mimeType:'application/pdf',buffer:pdfOriginal()},nome:'Conflito',campos:'[]',id:modelo.id,versao:'1'}});assert.equal(conflito.status(),409);
  const antigas=await(await api.get(`/api/assineai/editor/modelos/${modelo.id}/versoes`,{headers})).json();assert.equal(antigas.length,2);
  await pagina.locator('#studio-girar').evaluate(no=>{no.closest('details').open=true;});await pagina.locator('#studio-girar').click();
  await pagina.waitForFunction(()=>document.querySelector('#studio-status').textContent.includes('Páginas atualizadas'));
  assert.equal(await pagina.locator('#template-canvas').evaluate(no=>no.style.aspectRatio),'842 / 595');
  await pagina.locator('#studio-desfazer').click();await pagina.waitForFunction(()=>document.querySelector('#template-canvas').style.aspectRatio==='595 / 842').catch(async e=>{console.error({status:await pagina.locator('#studio-status').textContent(),ratio:await pagina.locator('#template-canvas').evaluate(n=>n.style.aspectRatio),erros});throw e;});
  await pagina.locator('#studio-duplicar-pagina').evaluate(no=>{no.closest('details').open=true;});await pagina.locator('#studio-duplicar-pagina').click();
  await pagina.waitForFunction(()=>document.querySelector('#studio-pagina').textContent==='Página 2 de 2').catch(async e=>{console.error({status:await pagina.locator('#studio-status').textContent(),erros});await pagina.screenshot({path:destino+'/falha.png',fullPage:true});throw e;});
  await pagina.locator('#studio-desfazer').click();await pagina.waitForFunction(()=>document.querySelector('#studio-pagina').textContent==='Página 1 de 1');
  await pagina.locator('#studio-preparar-convites').click();await pagina.locator('[data-pessoa=text]').fill('Ana Silva');await pagina.locator('[data-pessoa=email]').fill('ana@example.test');
  const conviteCriado=pagina.waitForResponse(r=>r.url().endsWith('/editor/convites')&&r.request().method()==='POST');
  await pagina.locator('#studio-criar-convites').click();const resposta=await conviteCriado;assert(resposta.ok(),await resposta.text());const envelope=await resposta.json();
  assert.equal(envelope.convites.length,1);
  await pagina.waitForFunction(()=>document.querySelector('#studio-status').textContent.includes('Convites criados'));
  await pagina.locator('.studio-acoes details').evaluate(no=>no.open=false);await pagina.locator('#documentos-ferramentas').evaluate(no=>no.scrollTop=0);
  await pagina.screenshot({path:destino+'/bancada.png',fullPage:true});
  const convite=envelope.convites[0].token;
  const dados=await(await api.get('/api/convites/'+convite)).json();assert.equal(dados.campos.length,5);
  const recusa=await api.post(`/api/convites/${convite}/assinar`,{multipart:{aceite:'true',valores:'{}'}});assert.equal(recusa.status(),400);
  const tela=await contexto.newPage();tela.on('pageerror',e=>erros.push(e.message));await tela.goto(base+'/convite/'+convite);
  await tela.waitForSelector('#area-assinatura:not([hidden])');
  await tela.locator('[data-campo-preenchimento][type=text]').fill('Ana Silva');await tela.locator('[data-campo-preenchimento][type=date]').fill('2026-11-05');await tela.locator('[data-campo-preenchimento][type=checkbox]').check();
  await tela.locator('[data-campo-preenchimento][type=file]').setInputFiles({name:'comprovante.pdf',mimeType:'application/pdf',buffer:pdfOriginal()});
  await tela.locator('#desenho-assinatura').scrollIntoViewIfNeeded();const desenho=await tela.locator('#desenho-assinatura').boundingBox();await tela.mouse.move(desenho.x+20,desenho.y+30);await tela.mouse.down();await tela.mouse.move(desenho.x+150,desenho.y+70,{steps:8});await tela.mouse.up();
  await tela.locator('#aceite').check();const envio=tela.waitForResponse(r=>r.url().endsWith('/assinar')&&r.request().method()==='POST');await tela.locator('#enviar-assinatura').click();const assinado=await envio.catch(async e=>{console.error('Erro do convite:',await tela.locator('#erro').textContent());throw e;});assert.equal(assinado.status(),204);
  let resultado;for(let tentativa=0;tentativa<8;tentativa++){resultado=await api.get(`/api/convites/${convite}/pdf`);if(resultado.status()!==429)break;await new Promise(r=>setTimeout(r,300));}assert(resultado.ok(),`PDF após assinatura: ${resultado.status()} ${resultado.ok()?'':await resultado.text()}`);fs.writeFileSync(destino+'/preenchido.pdf',await resultado.body());
  const depois=await(await api.get(`/api/convites/${convite}`)).json();assert.equal(depois.assinado,true);assert(depois.campos.some(c=>c.valor==='Ana Silva'));
  const campoAnexo=depois.campos.find(c=>c.tipo==='ANEXO');assert(campoAnexo.valor.includes('SHA-256'));
  const anexo=await api.get(`/api/convites/${convite}/anexos/${campoAnexo.id}`);assert.equal(anexo.status(),200);assert.deepEqual(await anexo.body(),pdfOriginal());
  const privado=await api.get(`/api/assineai/editor/envelopes/${envelope.identificador}/anexos/${campoAnexo.id}`,{headers:{Authorization:`Bearer ${outro}`}});assert.equal(privado.status(),404);
  assert.equal((await api.get(`/api/assineai/editor/envelopes/${envelope.identificador}/anexos/${campoAnexo.id}`,{headers})).status(),200);
  const certificado=process.env.FIRMA_TEST_CERTIFICATE;
  if(certificado){
    const final=await api.post(`/api/assineai/editor/envelopes/${envelope.identificador}/finalizar`,{headers,multipart:{certificado:{name:'teste.p12',mimeType:'application/x-pkcs12',buffer:fs.readFileSync(certificado)},senha:'senha-teste'}});
    assert.equal(final.status(),200);const bytes=await final.body();assert(bytes.includes(Buffer.from('/ByteRange')));fs.writeFileSync(destino+'/final.pdf',bytes);
    assert((await(await api.get(`/api/assineai/editor/envelopes/${envelope.identificador}`,{headers})).json()).finalizadoEm);
  }
  assert.deepEqual(erros,[]);
  await pagina.setViewportSize({width:390,height:844});await pagina.screenshot({path:destino+'/bancada-mobile.png',fullPage:true});
  console.log('OK: backend Java real, modelos, revisões, isolamento, conflitos, rotação, duplicação, histórico, convite, preenchimento e assinatura.');
 }finally{await navegador.close();await api.dispose();}
})().catch(e=>{console.error(e);process.exitCode=1;});
