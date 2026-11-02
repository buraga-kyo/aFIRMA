(async()=>{
 const resultados=[],esperar=(ms=20)=>new Promise(r=>setTimeout(r,ms));
 const assert=(ok,msg)=>{if(!ok)throw Error(msg);};
 async function teste(nome,fn){try{await fn();resultados.push({nome,ok:true});}catch(e){resultados.push({nome,ok:false,erro:e.stack});}}
 const origem=document.createElement('p');origem.id='chat-aviso';origem.dataset.firmaMensagem='';origem.textContent='Conecte o token no rodapé para carregar as conversas.';document.body.append(origem);await esperar();
 await teste('Aviso inicial usa o recado comum, com X e posição fora do eixo',()=>{
  const card=document.querySelector('.firma-mensagem'),b=card.querySelector('button');assert(card.textContent.includes('Conecte o token'),'Texto ausente');assert(b.getAttribute('aria-label')==='Fechar mensagem','X sem nome acessível');assert(getComputedStyle(origem).display==='none','Aviso antigo continua sobre o botão');const rect=document.getElementById('firma-mensagens').getBoundingClientRect();const x=rect.left+rect.width/2,y=rect.top+rect.height/2;assert(Math.abs(x-innerWidth/2)>4&&Math.abs(x-innerWidth/2)<100,'Posição horizontal não é deslocada do centro');assert(Math.abs(y-innerHeight/2)>4&&Math.abs(y-innerHeight/2)<100,'Posição vertical fora do padrão');
 });
 await teste('X fecha de verdade e alterações da página não ressuscitam o mesmo aviso',async()=>{
  document.querySelector('.firma-mensagem-fechar').click();await esperar();const div=document.createElement('div');div.textContent='Atualização da tabela';document.body.append(div);await esperar();assert(!document.querySelector('.firma-mensagem'),'Aviso fechado reapareceu');assert(!document.getElementById('firma-mensagens').matches(':popover-open'),'Camada vazia permanece aberta');
 });
 await teste('Limpar a mensagem de origem remove o recado imediatamente ao conectar',async()=>{
  origem.textContent='Conectando…';await esperar();assert(document.querySelector('.firma-mensagem'),'Aviso novo ausente');origem.textContent='';await esperar();assert(!document.querySelector('.firma-mensagem'),'Aviso vazio permaneceu visível');
 });
 await teste('Erros são fecháveis sobre dialog e não roubam o foco do formulário',async()=>{
  const dialog=document.createElement('dialog');dialog.innerHTML='<input id="campo-teste"><p id="erro-teste" data-firma-mensagem hidden></p>';document.body.append(dialog);dialog.showModal();const input=dialog.querySelector('input');input.focus();const erro=dialog.querySelector('p');erro.textContent='O acesso expirou.';erro.hidden=false;await esperar();const card=document.querySelector('.firma-mensagem');assert(card.dataset.tipo==='erro','Tipo do erro incorreto');assert(document.activeElement===input,'Aviso roubou foco da edição');const b=card.querySelector('button'),rect=b.getBoundingClientRect();assert(document.elementFromPoint(rect.left+rect.width/2,rect.top+rect.height/2)===b,'Dialog impede fechar o aviso');b.click();await esperar();assert(!document.querySelector('.firma-mensagem'),'X não fechou erro');erro.textContent='Outro erro';await esperar();assert(document.querySelector('.firma-mensagem'),'Erro seguinte ausente');dialog.close();await esperar();assert(!document.querySelector('.firma-mensagem'),'Erro de formulário fechado permaneceu aberto');dialog.remove();
 });
 await teste('Mensagens informativas expiram; passar o mouse pausa o fechamento',async()=>{
  FirmaMensagens.mostrar('Salvo.',{id:'salvo',duracao:60});const card=document.querySelector('[data-mensagem-id=salvo]');card.dispatchEvent(new Event('mouseenter'));await esperar(90);assert(card.isConnected,'Aviso expirou durante a leitura');card.dispatchEvent(new Event('mouseleave'));await esperar(90);assert(!card.isConnected,'Aviso não expirou');
 });
 await teste('Conteúdo é texto seguro e todas as mensagens têm X',()=>{
  FirmaMensagens.mostrar('<img src=x onerror=alert(1)>',{id:'seguro',duracao:0});const card=document.querySelector('[data-mensagem-id=seguro]');assert(!card.querySelector('img')&&card.textContent.includes('<img'),'Conteúdo interpretado como HTML');assert(card.querySelector('.firma-mensagem-fechar'),'Mensagem sem X');FirmaMensagens.limpar();
 });
 await teste('Página não gerou erros JavaScript',()=>assert(!window.errosMensagens.length,window.errosMensagens.join('\n')));
 const out=document.createElement('pre');out.id='resultado-testes';out.textContent=JSON.stringify(resultados);document.body.append(out);
})();
