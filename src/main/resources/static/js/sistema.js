(() => {
 const $=id=>document.getElementById(id),login=$('firma-login'),busca=$('firma-busca'),links=[...document.querySelectorAll('.firma-menu a')];
 const atual=links.find(a=>a.getAttribute('aria-current')==='page');$('firma-pagina').textContent=atual?.querySelector('span:last-child')?.textContent.trim()||'Zapiou';
 function aviso(texto){const painel=$('autentica-aviso')||$('chat-aviso')||$('indicadores-aviso');if(painel)painel.textContent=texto;}
 function abrirAcesso(){if($('lista-token')){$('lista-token').focus();return;}$('firma-chave').value=localStorage.getItem('assineai-chave')||'';$('firma-token').value=localStorage.getItem('assineai-token')||'';login.showModal();$('firma-token').focus();}
 $('firma-entrar').onclick=$('firma-conta').onclick=abrirAcesso;
 document.querySelector('[data-zapiou-acesso]')?.addEventListener('click',abrirAcesso);
 document.querySelectorAll('[data-firma-fechar]').forEach(b=>b.onclick=()=>b.closest('dialog').close());
 function montarGrupos(){
  const host=$('zapiou-grupos');if(!host)return;
  document.querySelectorAll('.zapiou-menu-popover').forEach(p=>p.remove());host.replaceChildren();
  const grupos=new Map();let grupo;
  for(const a of links){if(a.previousElementSibling?.classList.contains('firma-menu-grupo'))grupo=a.previousElementSibling.textContent;if(a.hidden||grupo==='aFIRMA')continue;if(!grupos.has(grupo))grupos.set(grupo,[]);grupos.get(grupo).push(a);}
  let indice=0;
  for(const [titulo,itens] of grupos){
   const botao=document.createElement('button'),menu=document.createElement('div');botao.type='button';botao.className='trator-aba zapiou-grupo-aba';botao.textContent=titulo.toLocaleUpperCase('pt-BR');botao.dataset.grupo=titulo;botao.setAttribute('aria-expanded','false');botao.setAttribute('aria-haspopup','menu');
   menu.id='zapiou-menu-'+indice++;menu.className='zapiou-menu-popover';menu.setAttribute('popover','auto');menu.setAttribute('aria-label',titulo);botao.setAttribute('aria-controls',menu.id);
   for(const a of itens)menu.append(a.cloneNode(true));
   menu.addEventListener('toggle',()=>botao.setAttribute('aria-expanded',String(menu.matches(':popover-open'))));
   botao.onclick=()=>{if(menu.matches(':popover-open')){menu.hidePopover();return;}const rect=botao.getBoundingClientRect();menu.style.left=Math.max(8,Math.min(rect.left,innerWidth-268))+'px';menu.style.top=rect.bottom+'px';menu.style.maxHeight=Math.max(120,innerHeight-rect.bottom-16)+'px';menu.showPopover();};
   document.body.append(menu);host.append(botao);
  }
 }
 montarGrupos();
 function resultados(){const q=$('firma-busca-input').value.toLocaleLowerCase('pt-BR');$('firma-busca-resultados').replaceChildren();links.filter(a=>a.textContent.toLocaleLowerCase('pt-BR').includes(q)).forEach(a=>{const b=a.cloneNode(true);b.removeAttribute('aria-current');$('firma-busca-resultados').append(b);});}
 function abrirBusca(){busca.showModal();$('firma-busca-input').value='';resultados();$('firma-busca-input').focus();}
 document.querySelectorAll('[data-firma-buscar]').forEach(b=>b.onclick=abrirBusca);$('firma-busca-input').oninput=resultados;
 document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();abrirBusca();}});
 $('firma-menu-toggle').onclick=()=>{const mobile=matchMedia('(max-width:850px)').matches;document.body.classList.toggle(mobile?'firma-menu-aberto':'firma-menu-recolhido');$('firma-menu-toggle').setAttribute('aria-expanded',String(document.body.classList.contains('firma-menu-aberto')));};
 async function consultar(token){const r=await fetch('/api/autentica/contexto',{headers:{Authorization:'Bearer '+token},cache:'no-store'});if(!r.ok)throw Error('Token inválido ou expirado. Informe um JWT válido.');return r.json();}
 let versaoConexao=0;
 function desconectar(){versaoConexao++;delete window.FirmaAcesso;delete window.FirmaContexto;document.body.classList.remove('firma-autenticado');$('firma-sessao').textContent='Acesse seu espaço';$('firma-sair').hidden=true;$('firma-entrar').textContent='Conectar acesso';if($('lista-conectar'))$('lista-conectar').textContent='✓ CONECTAR';}
 document.addEventListener('firma:desconectado',desconectar);
 async function conectar(chave,token){
  document.dispatchEvent(new CustomEvent('firma:desconectado'));
  token=token.trim();if(!token)throw Error('Informe o token JWT.');const versao=++versaoConexao,d=await consultar(token);
  if(versao!==versaoConexao)throw Error('As credenciais foram alteradas. Conecte novamente.');
  localStorage.setItem('assineai-token',token);if(chave.trim())localStorage.setItem('assineai-chave',chave.trim());else localStorage.removeItem('assineai-chave');ativar(d,token);if(login.open)login.close();
 }
 $('firma-login-form').onsubmit=async e=>{e.preventDefault();const b=e.target.querySelector('[type=submit]');b.disabled=true;$('firma-login-erro').textContent='';try{await conectar($('firma-chave').value,$('firma-token').value);}catch(e){$('firma-login-erro').textContent=e.message;}finally{b.disabled=false;}};
 $('firma-sair').onclick=async()=>{try{await fetch('/api/autentica/logout',{method:'POST',headers:{Authorization:'Bearer '+localStorage.getItem('assineai-token')}});}catch{}for(const k of ['assineai-token','assineai-chave','chat-token','chat-chave'])localStorage.removeItem(k);location.reload();};
 if($('lista-conectar')){
  $('lista-chave').value=localStorage.getItem('assineai-chave')||'';$('lista-token').value=localStorage.getItem('assineai-token')||'';
  $('lista-conectar').onclick=async()=>{const b=$('lista-conectar');b.disabled=true;aviso('Conectando…');try{await conectar($('lista-chave').value,$('lista-token').value);}catch(e){aviso(e.message);}finally{b.disabled=false;}};
  for(const id of ['lista-chave','lista-token'])$(id).addEventListener('input',()=>document.dispatchEvent(new CustomEvent('firma:desconectado')));
  const teste=$('usar-credenciais-teste');if(teste&&document.body.dataset.tokenTeste&&document.body.dataset.chaveTeste){teste.hidden=false;teste.onclick=()=>{$('lista-chave').value=document.body.dataset.chaveTeste;$('lista-token').value=document.body.dataset.tokenTeste;$('lista-conectar').click();};if(!$('lista-chave').value&&!$('lista-token').value){$('lista-chave').value=document.body.dataset.chaveTeste;$('lista-token').value=document.body.dataset.tokenTeste;}}
 }
 function ativar(d,token){window.FirmaContexto=d;window.FirmaAcesso=Object.freeze({token,contexto:d});const permitidos=new Set(d.modulos.map(m=>m.slug));links.forEach(a=>{const u=new URL(a.href);if(u.pathname==='/sistema')a.hidden=!permitidos.has(u.searchParams.get('modulo')==='indicadores'?'atendimentos':u.searchParams.get('modulo'));});montarGrupos();document.body.classList.add('firma-autenticado');$('firma-empresa').textContent=d.nome||'Sua empresa';$('firma-usuario').textContent=d.nome||'Credenciais';$('firma-sessao').textContent='Acesso conectado';$('firma-entrar').textContent='Credenciais';$('firma-sair').hidden=false;if($('lista-conectar'))$('lista-conectar').textContent='✓ CONECTADO';document.dispatchEvent(new CustomEvent('firma:contexto',{detail:d}));}
 async function contexto(){const token=localStorage.getItem('assineai-token');if(!token)return;try{ativar(await consultar(token),token);}catch(e){aviso(e.message);}}
 window.addEventListener('storage',e=>{if(e.key==='assineai-token')location.reload();});
 if(!document.body.classList.contains('sistema-trator'))contexto();
})();
