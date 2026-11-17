// Antes: mvnw -Dtest=NavegacaoTratorTest test. Requer Playwright no NODE_PATH e Google Chrome instalado. Usa API simulada, sem publicar provas.
const {chromium}=require('playwright');
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const raiz=path.resolve(__dirname,'../src/main/resources');
let config={cofreDisponivel:true,nostr:{},bitcoin:{}},recebidos=[],negado=false;
const servidor=http.createServer(async(req,res)=>{
  res.setHeader('Content-Type','application/json');
  if(req.url.startsWith('/api/')){
    if(negado){res.statusCode=403;res.end(JSON.stringify({detail:'Somente administrador da empresa'}));return;}
    let texto='';for await(const parte of req)texto+=parte;
    const dados=texto?JSON.parse(texto):null;
    if(req.method==='PUT'){
      recebidos.push({rota:req.url,dados});
      if(req.url.endsWith('/nostr'))config.nostr={referencia:dados.referencia,chavePublica:dados.chavePublica,chaveClientePublica:'33'.repeat(32),relays:dados.relays,bunkerSalvo:true};
      else config.bitcoin=dados.remover?{}:{endereco:dados.endereco,usuario:dados.usuario,senhaSalva:true};
    }
    if(req.url.endsWith('/bitcoin/testar'))res.end(JSON.stringify({rede:'main',sincronizado:true,blocos:900000}));
    else if(req.url.endsWith('/nostr/testar'))res.end(JSON.stringify({chavePublica:'11'.repeat(32)}));
    else res.end(JSON.stringify(config));
    return;
  }
  if(req.url==='/js/acesso-compartilhado.js'){res.setHeader('Content-Type','text/javascript');res.end('');return;}
  const arquivo=req.url==='/'?path.resolve(__dirname,'../target/telas-trator-test/inventor-configuracao.html'):path.join(raiz,'static'+req.url);
  if(!fs.existsSync(arquivo)){res.statusCode=404;res.end();return;}
  res.setHeader('Content-Type',arquivo.endsWith('.js')?'text/javascript':arquivo.endsWith('.css')?'text/css':'text/html; charset=utf-8');res.end(fs.readFileSync(arquivo));
});
(async()=>{
 await new Promise(r=>servidor.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({executablePath:'/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});
 try{
  const pagina=await browser.newPage();const erros=[];pagina.on('pageerror',e=>erros.push(e.message));
  await pagina.addInitScript(()=>localStorage.setItem('assineai-token','admin-teste'));
  await pagina.goto('http://127.0.0.1:'+servidor.address().port);await pagina.waitForFunction(()=>!document.querySelector('fieldset').disabled);
  await pagina.fill('#conexao-publica','11'.repeat(32));await pagina.fill('#conexao-bunker','bunker://segredo-teste');await pagina.fill('#conexao-relays','wss://um.example\nwss://dois.example');
  await pagina.locator('#conexao-nostr button[type=submit]').click();await pagina.waitForFunction(()=>document.getElementById('conexao-nostr-estado').textContent.includes('Conexão salva'));
  assert.equal(await pagina.inputValue('#conexao-bunker'),'');assert.equal(recebidos[0].dados.bunker,'bunker://segredo-teste');
  await pagina.click('#conexao-nostr-testar');await pagina.waitForFunction(()=>document.getElementById('conexao-nostr-estado').textContent.includes('Assinador conferido'));
  await pagina.fill('#conexao-rpc','https://rpc.example');await pagina.fill('#conexao-rpc-usuario','rpc');await pagina.fill('#conexao-rpc-senha','senha-teste');
  await pagina.locator('#conexao-bitcoin button[type=submit]').click();await pagina.waitForFunction(()=>document.getElementById('conexao-bitcoin-estado').textContent.includes('Configuração salva'));
  assert.equal(await pagina.inputValue('#conexao-rpc-senha'),'');
  await pagina.click('#conexao-bitcoin-testar');await pagina.waitForFunction(()=>document.getElementById('conexao-bitcoin-estado').textContent.includes('900000'));
  await pagina.click('#conexao-bitcoin-remover');await pagina.waitForFunction(()=>document.getElementById('conexao-bitcoin-estado').textContent.includes('removida'));
  assert.equal(await pagina.inputValue('#conexao-rpc'),'');
  await pagina.setViewportSize({width:390,height:844});assert(await pagina.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Página ultrapassa largura do celular');
  await pagina.screenshot({path:'/tmp/afirma-inventor-conexoes-celular.png',fullPage:true});
  negado=true;await pagina.click('#conexoes-atualizar');await pagina.waitForFunction(()=>document.getElementById('conexoes-estado').textContent.includes('Somente administrador'));
  assert(await pagina.locator('#conexao-publica').isDisabled());assert.deepEqual(erros,[]);
  assert(!await pagina.evaluate(()=>Object.values(localStorage).some(v=>v.includes('senha-teste')||v.includes('bunker://'))));
  console.log('OK: cadastro Nostr/Bitcoin, testes de conexão, remoção, limpeza de credenciais, bloqueio 403 e layout móvel.');
 }finally{await browser.close();servidor.close();}
})().catch(e=>{console.error(e);servidor.close();process.exitCode=1;});
