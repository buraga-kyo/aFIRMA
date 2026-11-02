(() => {
 const $=id=>document.getElementById(id);let versaoLista=0;let resposta=null,conversaAtual=null,contexto,atual=null,todos=[],caches={},timer,versao=0,temMaisMensagens=false;
 async function api(caminho,method='GET',dados){
  const acesso=window.FirmaAcesso;if(!contexto||!acesso)throw Error('Conecte um token válido no rodapé.');
  const r=await fetch('/api/autentica/'+caminho,{method,headers:{Authorization:`Bearer ${acesso.token}`,...(dados?{'Content-Type':'application/json'}:{})},...(dados?{body:JSON.stringify(dados)}:{}),cache:'no-store'});
  if(acesso!==window.FirmaAcesso)throw Error('A conexão foi alterada. Conecte novamente.');
  const d=await r.json().catch(()=>({}));if(!r.ok){if(r.status===401)document.dispatchEvent(new CustomEvent('firma:desconectado'));throw Error(d.mensagem||d.message||`Operação recusada (${r.status}).`);}return d;
 }
 async function registros(m){return caches[m]||=(await api('registros/'+m+'?tamanho=100')).itens;}
 function nome(r){return r.ContatoNome||r.ContatoNumero||r.CanalNome||r.SetorNome||r.AtendenteNome||`Registro ${r.id}`;}
 function opcoes(el,itens,selecionado){el.replaceChildren(new Option('Selecione…',''));for(const r of itens)el.add(new Option(nome(r),r.id));if(selecionado)el.value=selecionado;}
 function aviso(texto,erro=false){$('chat-aviso').dataset.firmaTipo=erro?'erro':'info';$('chat-aviso').textContent=texto;}
 function podeEditarContato(){return Boolean(contexto?.modulos.find(m=>m.slug==='contatos')?.editavel);}
 async function salvarContato(item,alteracoes){
  if(!podeEditarContato())throw Error('Seu perfil não permite editar contatos.');
  const mapa={contato:'ContatoNome',telefone:'ContatoNumero',email:'ContatoEmail'},dados={};
  for(const [campo,valor]of Object.entries(alteracoes))if(mapa[campo])dados[mapa[campo]]=String(valor).trim();
  if('ContatoNome' in dados&&!dados.ContatoNome)throw Error('Informe o nome do contato.');
  const contato=await api('registros/contatos/'+item.ContatoId,'PUT',dados);
  todos=todos.map(a=>a.ContatoId===item.ContatoId?{...a,...dados,ContatoNome:contato.ContatoNome??dados.ContatoNome??a.ContatoNome,ContatoNumero:contato.ContatoNumero??dados.ContatoNumero??a.ContatoNumero,ContatoEmail:contato.ContatoEmail??dados.ContatoEmail??a.ContatoEmail}:a);
  await desenhar();return {...alteracoes};
 }
 function renderAcoes(item,td){
  td.classList.add('acoes-padrao');const executar=fn=>async()=>{try{await fn();}catch(e){aviso(e.message,true);}};
  td.append(FirmaTabela.botaoAcao('visualizar','Visualizar '+item.contato,executar(()=>{tabela.detalhes(item);const abrirChat=document.createElement('button');abrirChat.type='button';abrirChat.textContent='ABRIR CONVERSA ↧';abrirChat.onclick=()=>{tabela.dialogo?.close();aberta=true;abrir(item.id).catch(e=>aviso(e.message,true));};tabela.dialogo.querySelector('.af-detalhes-acoes').append(abrirChat);})));
  const editar=FirmaTabela.botaoAcao('editar','Editar contato '+item.contato);editar.disabled=!podeEditarContato();td.append(editar);
  td.append(FirmaTabela.botaoAcao('historico','Histórico de '+item.contato,executar(()=>historico(item.id))));
  const finalizado=item.AtendimentoStatus==='finalizado';td.append(FirmaTabela.botaoAcao(finalizado?'reativar':'finalizar',finalizado?'Reativar atendimento':'Finalizar atendimento',executar(async()=>{await api('conversas/'+item.id+'/acao','POST',{acao:finalizado?'reativar':'finalizar'});await listar();if(atual===item.id)await abrir(item.id);aviso(finalizado?'Atendimento reativado.':'Atendimento finalizado.');})));
 }
 async function historico(id){
  const itens=await api('conversas/'+id+'/historico');$('chat-historico-conteudo').replaceChildren();
  for(const a of itens){const b=document.createElement('button');b.type='button';b.textContent=(a.AtendimentoProtocolo||a.id)+' · '+a.AtendimentoStatus;b.onclick=()=>{$('chat-historico-dialog').close();aberta=true;abrir(a.id).catch(e=>aviso(e.message,true));};$('chat-historico-conteudo').append(b);}
  $('chat-historico-dialog').showModal();
 }
 async function listar(){
  if(!contexto||!window.FirmaAcesso){$('chat-aviso').textContent='Conecte o token no rodapé para carregar as conversas.';return;}
  const requisicao=++versaoLista,busca=$('chat-busca').value;
  try{const resultado=[];let pagina=1,total;
   do{const d=await api('registros/atendimentos?'+new URLSearchParams({tamanho:100,pagina,busca}));if(requisicao!==versaoLista)return;total=d.total;resultado.push(...d.itens);pagina++;if(!d.itens.length)break;}while(resultado.length<total);
   todos=resultado;await desenhar();$('chat-aviso').textContent='';
  }catch(e){if(requisicao===versaoLista)$('chat-aviso').textContent=e.message;}
 }
 function avatar(contato){
  const el=document.createElement('span');el.className='zapiou-contato-avatar';el.textContent=nome(contato).trim().split(/\s+/).slice(0,2).map(p=>p[0]).join('').toLocaleUpperCase('pt-BR');
  if(contato.ContatoAvatar){try{const url=new URL(contato.ContatoAvatar,location.href);if(['https:','http:'].includes(url.protocol)||/^data:image\/(png|jpeg|webp|gif);base64,/i.test(contato.ContatoAvatar)){
   const img=document.createElement('img');img.alt='Foto de '+nome(contato);img.loading='lazy';img.referrerPolicy='no-referrer';img.src=url.href;img.onerror=()=>img.remove();el.append(img);
  }}catch{}}
  return el;
 }
 function contatoCelula(a,td){
  td.dataset.conversa=a.id;const pessoa=document.createElement('div'),dados=document.createElement('span'),titulo=document.createElement('strong'),telefone=document.createElement('small');
  pessoa.className='zapiou-contato';dados.className='zapiou-contato-identidade';titulo.textContent=a.contato;telefone.textContent=a.ContatoNumero||'Telefone não informado';dados.append(titulo,telefone);pessoa.append(avatar(a),dados);td.append(pessoa);

 }
 let tabela,integral=false,aberta=false,larguraConfigurada=false,observadorChat;
 const main=document.querySelector('main.trator-relacionamento'),estacionamento=$('chat-estacionamento'),painel=document.createElement('div');painel.id='chat-painel';painel.className='autentica-chat';painel.append(document.querySelector('.autentica-chat-body'),$('chat-detalhes'));estacionamento.append(painel);
 function estacionar(){estacionamento.append(painel);}
 function ajustarLarguraChat(){const scroll=$('chat-tabela').querySelector('.tabela-scroll');if(scroll&&!integral)painel.style.setProperty('--zapiou-chat-largura',scroll.clientWidth+'px');}
 function encaixar(){
  estacionar();document.querySelectorAll('.zapiou-linha-chat').forEach(tr=>tr.remove());
  if(integral){main.append(painel);return;}
  if(!aberta||!atual){estacionar();return;}
  const linha=document.querySelector('[data-conversa-id="'+atual+'"]');if(!linha){estacionar();return;}
  const tr=document.createElement('tr'),td=document.createElement('td');tr.className='zapiou-linha-chat';td.colSpan=linha.children.length;td.append(painel);tr.append(td);linha.after(tr);ajustarLarguraChat();
 }
 $('chat-recolher').onclick=()=>{const id=atual;versao++;atual=null;conversaAtual=null;aberta=false;integral=false;document.body.classList.remove('zapiou-integral');$('chat-expandir').textContent='EXPANDIR CHAT ↗';$('chat-conversa').hidden=true;$('chat-vazio').hidden=false;encaixar();document.querySelectorAll('.autentica-chat-item.ativo').forEach(b=>b.classList.remove('ativo'));document.querySelector('[data-conversa-id="'+id+'"] .btn-ver-linha')?.focus({preventScroll:true});};
 $('chat-expandir').onclick=()=>{integral=!integral;document.body.classList.toggle('zapiou-integral',integral);$('chat-expandir').textContent=integral?'VOLTAR AO TRATOR ↙':'EXPANDIR CHAT ↗';encaixar();};
 function prepararFaixa(instancia){
  const faixa=instancia.raiz.querySelector('.toolbar-faixa>.toolbar-grupo');
  if(faixa.dataset.arrastavel)return;
  const nav=faixa.querySelector('.zapiou-navegacao'),grupos=$('zapiou-grupos');
  const acoes=[$('chat-nova'),$('importar-dados'),$('exportar-dados'),instancia.no('colunas-botao')];
  for(const botao of acoes){botao.classList.add('trator-aba');nav.insertBefore(botao,grupos);}
  faixa.dataset.arrastavel='true';faixa.classList.add('zapiou-faixa-abas');faixa.tabIndex=0;
  faixa.setAttribute('role','region');faixa.setAttribute('aria-label','Abas do trator. Arraste para os lados ou use as setas.');
  const busca=instancia.no('busca-form').parentElement;busca.classList.add('zapiou-burro-fixo');
  let gesto=null,bloquearClique=false;
  const fecharMenus=()=>{document.querySelectorAll('.zapiou-menu-popover:popover-open').forEach(m=>m.hidePopover());instancia.fecharMenus();};
  faixa.addEventListener('pointerdown',e=>{
   bloquearClique=false;
   if(e.pointerType==='touch'||e.button!==0||e.target.closest('input,textarea,select,label'))return;
   gesto={id:e.pointerId,x:e.clientX,y:e.clientY,inicio:faixa.scrollLeft,arrastou:false};
  });
  faixa.addEventListener('pointermove',e=>{
   if(!gesto||e.pointerId!==gesto.id)return;
   const dx=e.clientX-gesto.x,dy=e.clientY-gesto.y;
   if(!gesto.arrastou){if(Math.abs(dx)<6||Math.abs(dx)<Math.abs(dy))return;gesto.arrastou=true;bloquearClique=true;faixa.setPointerCapture(e.pointerId);faixa.classList.add('arrastando');fecharMenus();}
   e.preventDefault();faixa.scrollLeft=gesto.inicio-dx;
  });
  const encerrar=e=>{if(!gesto||e.pointerId!==gesto.id)return;gesto=null;faixa.classList.remove('arrastando');};
  faixa.addEventListener('pointerup',encerrar);faixa.addEventListener('pointercancel',encerrar);faixa.addEventListener('lostpointercapture',encerrar);
  window.addEventListener('pointerup',encerrar);
  faixa.addEventListener('click',e=>{if(bloquearClique){e.preventDefault();e.stopImmediatePropagation();bloquearClique=false;}},true);
  faixa.addEventListener('dragstart',e=>e.preventDefault());
  faixa.addEventListener('scroll',fecharMenus,{passive:true});
  faixa.addEventListener('keydown',e=>{
   if(e.target!==faixa)return;
   if(e.key==='ArrowRight'||e.key==='ArrowLeft'){e.preventDefault();faixa.scrollLeft+=e.key==='ArrowRight'?180:-180;}
   else if(e.key==='Home'||e.key==='End'){e.preventDefault();faixa.scrollLeft=e.key==='Home'?0:faixa.scrollWidth;}
  });
  faixa.addEventListener('focusin',e=>{
   if(e.target===faixa)return;
   const r=e.target.getBoundingClientRect(),limite=busca.getBoundingClientRect().left-16,inicio=faixa.getBoundingClientRect().left;
   if(r.right>limite)faixa.scrollLeft+=r.right-limite;
   else if(r.left<inicio)faixa.scrollLeft+=r.left-inicio;
  });
 }
 async function desenhar(){
  const itens=todos.map(a=>({...a,contato:nome(a),ultima:a.UltimaMensagem||'Sem mensagem',telefone:a.ContatoNumero||'',email:a.ContatoEmail||'',status:a.AtendimentoStatus==='finalizado'?'Finalizados':a.AtendimentoBOTExterno&&a.AtendimentoStatus==='emandamento'?'Bots':a.AtendimentoStatus==='emandamento'?'Em andamento':'Pendente',canal:a.CanalNome||'Sem canal',naoLidas:a.AtendimentoQtdMensagensNaoLidas||0}));
  if(!tabela)tabela=FirmaTrator.montar({elemento:'#chat-tabela',rotulo:'Conversas do Zapiou',dados:[],slots:{comandos:destino=>{destino.append(document.querySelector('.zapiou-navegacao'),$('chat-nova'),$('chat-expandir'),$('exportar-dados'),$('importar-dados'),$('chat-atualizar'));const busca=$('chat-busca').closest('label');busca.className='zapiou-busca';destino.append(busca);},rodape:$('chat-rodape').content.cloneNode(true)},edicaoInline:false,containerId:'zapiou',storageId:'zapiou-desconectado',toast:aviso,interpretarBusca:consulta=>api('plano-busca','POST',{consulta}),salvar:salvarContato,renderAcoes, colunas:[
   {id:'contato',nome:'CONTATO',largura:300,larguraMinima:220,ocultavel:false,render:contatoCelula,editavel:false},
   {id:'ultima',nome:'ÚLTIMA MSG',largura:360},{id:'status',nome:'ATENDIMENTOS',tipo:'enum',largura:180},{id:'canal',nome:'CANAL',largura:190},{id:'naoLidas',nome:'NÃO LIDAS',tipo:'numero',largura:130},{id:'updatedAt',nome:'ATUALIZAÇÃO',tipo:'data',largura:190},{id:'telefone',nome:'TELEFONE',visivel:false},{id:'email',nome:'E-MAIL',visivel:false}].map(c=>({...c,editavel:false})),onRender:instancia=>{prepararFaixa(instancia);document.querySelectorAll('[data-conversa]').forEach(td=>{const tr=td.closest('tr');tr.dataset.conversaId=td.dataset.conversa;tr.ondblclick=e=>{if(e.target.closest('button,a,input,select'))return;aberta=true;abrir(Number(td.dataset.conversa)).catch(e=>$('chat-aviso').textContent=e.message);};});encaixar();},textos:{busca:'FALAR COM O BURRO DE CARGA...',vazio:'Nenhuma conversa nesta fila. Dê dois cliques em um registro para abrir o chat.'}});
  if(!observadorChat&&window.ResizeObserver){observadorChat=new ResizeObserver(ajustarLarguraChat);observadorChat.observe($('chat-tabela').querySelector('.tabela-scroll'));}
  if(!larguraConfigurada){FirmaTrator.configurarLargura(main,$('alternar-largura'),'zapiou-largura-v1');larguraConfigurada=true;}
  for(const c of tabela.colunas)c.editavel=['contato','telefone','email'].includes(c.id)&&podeEditarContato();
  estacionar();tabela.definirDados(itens);encaixar();
  $('chat-lista').replaceChildren();for(const a of itens){const b=document.createElement('button');b.type='button';b.className='autentica-chat-item'+(atual===a.id?' ativo':'');const strong=document.createElement('strong'),small=document.createElement('small');strong.textContent=a.contato;small.textContent=a.ultima;const info=document.createElement('span');info.append(strong,small);b.append(avatar(a),info);b.onclick=()=>{aberta=true;abrir(a.id).catch(e=>$('chat-aviso').textContent=e.message);};$('chat-lista').append(b);}
 }
 async function abrir(id,anteriores=null){const v=++versao;const a=await api('conversas/'+id);if(v!==versao)return;const painel=$('chat-mensagens'),posicao=painel.scrollTop,altura=painel.scrollHeight,noFim=painel.scrollHeight-painel.scrollTop-painel.clientHeight<60;
  if(atual===id&&conversaAtual){const mapa=new Map(conversaAtual.mensagens.map(m=>[m.id,m]));for(const m of [...(anteriores||[]),...a.mensagens])mapa.set(m.id,m);a.mensagens=[...mapa.values()].sort((x,y)=>new Date(y.MensagemDataHoraWhatsApp||y.createdAt)-new Date(x.MensagemDataHoraWhatsApp||x.createdAt)||y.id-x.id);}else temMaisMensagens=a.mensagens.length===100;
  if(anteriores)temMaisMensagens=anteriores.length===100;if(atual!==id){resposta=null;$('chat-resposta').hidden=true;}atual=id;aberta=true;conversaAtual=a;$('chat-vazio').hidden=true;$('chat-conversa').hidden=false;$('chat-nome').textContent=nome(a.contato);$('chat-avatar').replaceChildren(avatar(a.contato));$('chat-protocolo').textContent=`${a.AtendimentoProtocolo||''} · ${a.canal.CanalNome||''}`;$('chat-mensagens').replaceChildren();
  if(temMaisMensagens){const mais=document.createElement('button');mais.id='chat-mensagens-anteriores';mais.type='button';mais.textContent='Carregar mensagens anteriores';mais.onclick=async()=>{mais.disabled=true;try{const d=await api('conversas/'+id+'/mensagens?antes='+a.mensagens.at(-1).id);if(atual===id)await abrir(id,d);}catch(e){$('chat-aviso').textContent=e.message;}finally{mais.disabled=false;}};$('chat-mensagens').append(mais);}
  for(const m of [...a.mensagens].reverse()){const b=document.createElement('div');b.className='autentica-bolha'+(m.MensagemFromMe?' saida':'');b.textContent=m.MensagemDeletada?'Mensagem excluída':m.MensagemTexto||(m.ArquivoId?'Arquivo anexado':'Mensagem sem texto');const s=document.createElement('small');s.textContent=new Date(m.MensagemDataHoraWhatsApp||m.createdAt).toLocaleString('pt-BR')+(m.MensagemEditada?' · editada':'')+(m.MensagemFromMe?' · '+({0:'Pendente',1:'Enviada',2:'Entregue',3:'Lida',4:'Reproduzida','-1':'Falha'}[m.MensagemACK]||'Enviada'):'');if(m.MensagemReacao?.length){const reacoes=document.createElement('small');reacoes.textContent=m.MensagemReacao.map(r=>r.Reacao).filter(Boolean).join(' ');b.append(reacoes);}b.append(s);
   if(m.MensagemRespostaId){const q=document.createElement('small');q.textContent='Em resposta à mensagem #'+m.MensagemRespostaId;b.prepend(q);}
   if(m.ArquivoId){const ar=document.createElement('button');ar.type='button';ar.textContent='Baixar anexo ↧';ar.onclick=()=>baixar(m.ArquivoId).catch(e=>$('chat-aviso').textContent=e.message);b.append(ar);}
   if(!m.MensagemDeletada){const responder=document.createElement('button');responder.type='button';responder.textContent='Responder';responder.onclick=()=>{resposta=m.id;$('chat-resposta').hidden=false;$('chat-resposta').querySelector('span').textContent=(m.MensagemTexto||'Arquivo').slice(0,100);$('chat-texto').focus();};b.append(responder);if(m.MensagemFromMe){const remover=document.createElement('button');remover.type='button';remover.textContent='Apagar';remover.onclick=async()=>{try{await api('conversas/'+id+'/mensagens/'+m.id+'/remover','POST');await abrir(id);}catch(e){$('chat-aviso').textContent=e.message;}};b.append(remover);}}
   $('chat-mensagens').append(b);}if(!a.mensagens.length){const p=document.createElement('p');p.textContent='O histórico deste atendimento começa aqui.';p.style.cssText='text-align:center;color:#a0aec0;font-size:11px';$('chat-mensagens').append(p);}$('chat-mensagens').scrollTop=anteriores?posicao+painel.scrollHeight-altura:noFim?painel.scrollHeight:posicao;
  $('chat-contato-dados').replaceChildren();for(const [rotulo,valor]of [['Nome',a.contato.ContatoNome],['Telefone',a.contato.ContatoNumero],['E-mail',a.contato.ContatoEmail],['Canal',a.canal.CanalNome],['Situação',a.AtendimentoStatus]]){const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=rotulo;dd.textContent=({pendente:'Pendente',emandamento:'Em atendimento',finalizado:'Finalizado'}[valor]||valor||'Não informado');$('chat-contato-dados').append(dt,dd);}
  opcoes($('chat-atendente'),await registros('atendentes'),a.AtendenteId);opcoes($('chat-setor'),(await registros('setores')).filter(s=>!s.CanalId||s.CanalId===a.CanalId),a.SetorId);$('chat-atividades').replaceChildren();for(const acao of a.acoes){const p=document.createElement('p');p.textContent=acao.AtendimentoAcaoTipo+' · '+new Date(acao.createdAt).toLocaleString('pt-BR');$('chat-atividades').append(p);}
  $('chat-reativar').hidden=a.AtendimentoStatus!=='finalizado';
  $('chat-assumir').hidden=a.AtendimentoStatus==='finalizado';
  try{const tags=(await registros('tags')).filter(t=>t.TagStatus!==false&&(!t.SetorId||t.SetorId===a.SetorId));opcoes($('chat-tags'),tags.map(t=>({...t,ContatoNome:t.TagNome})));for(const o of $('chat-tags').options)o.selected=(a.tags||[]).some(t=>String(t.id)===o.value);}catch{}
  try{opcoes($('chat-resposta-rapida'),(await registros('respostas')).filter(m=>!m.SetorId||m.SetorId===a.SetorId).map(m=>({...m,ContatoNome:m.MensagemRapidaTexto.slice(0,60)})));}catch{}
  try{opcoes($('chat-ia'),(await registros('ia')).filter(ia=>ia.InteligenciaArtificialStatus!==false&&(!ia.CanalId||ia.CanalId===a.CanalId)&&(!ia.SetorId||ia.SetorId===a.SetorId)).map(ia=>({...ia,ContatoNome:ia.InteligenciaArtificialNome})));}catch{}
  if(a.AtendimentoQtdMensagensNaoLidas)await api('conversas/'+id+'/acao','POST',{acao:'lidas'});
  $('chat-envio').hidden=a.AtendimentoStatus==='finalizado';$('chat-finalizar').disabled=a.AtendimentoStatus==='finalizado';await desenhar();}
 async function acao(acao,extra={}){if(!atual)return;try{await api(`conversas/${atual}/acao`,'POST',{acao,...extra});caches={};await listar();await abrir(atual);}catch(e){$('chat-aviso').textContent=e.message;}}
 $('chat-assumir').onclick=()=>acao('assumir',{AtendenteId:Number($('chat-atendente').value)});$('chat-transferir').onclick=()=>acao('transferir',{SetorId:Number($('chat-setor').value)});$('chat-finalizar').onclick=()=>acao('finalizar');
 $('chat-envio').onsubmit=async e=>{e.preventDefault();if(!atual)return;const b=e.target.querySelector('[type=submit]');b.disabled=true;$('chat-estado-envio').textContent='Enviando…';try{await api(`conversas/${atual}/mensagens`,'POST',{texto:$('chat-texto').value,...(resposta?{MensagemRespostaId:resposta}:{})});$('chat-texto').value='';resposta=null;$('chat-resposta').hidden=true;$('chat-estado-envio').textContent='Mensagem enviada';await abrir(atual);}catch(erro){$('chat-estado-envio').textContent=erro.message;}finally{b.disabled=false;}};

 $('chat-resposta-cancelar').onclick=()=>{resposta=null;$('chat-resposta').hidden=true;};
 $('chat-reativar').onclick=()=>acao('reativar');
 $('chat-avaliar').onclick=()=>acao('avaliar',{nota:Number($('chat-nota').value)});
 $('chat-tags-salvar').onclick=async()=>{try{await api('conversas/'+atual+'/tags','PUT',[...$('chat-tags').selectedOptions].map(o=>Number(o.value)).filter(Boolean));await abrir(atual);}catch(e){$('chat-aviso').textContent=e.message;}};
 $('chat-resposta-rapida').onchange=async()=>{const r=(await registros('respostas')).find(m=>m.id===Number($('chat-resposta-rapida').value));if(r)$('chat-texto').value=r.MensagemRapidaTexto;};
 $('chat-sugerir').onclick=async()=>{if(!atual||!$('chat-ia').value)return;const b=$('chat-sugerir');b.disabled=true;try{const r=await api('conversas/'+atual+'/ia/'+$('chat-ia').value,'POST');$('chat-texto').value=r.texto;}catch(e){$('chat-estado-envio').textContent=e.message;}finally{b.disabled=false;}};
 $('chat-anexo').onchange=async()=>{const arquivo=$('chat-anexo').files[0];if(!arquivo||!atual)return;const form=new FormData();form.append('arquivo',arquivo);form.append('legenda',$('chat-texto').value);try{const r=await fetch('/api/autentica/conversas/'+atual+'/arquivos',{method:'POST',headers:{Authorization:'Bearer '+localStorage.getItem('assineai-token')},body:form});if(!r.ok){const e=await r.json();throw Error(e.mensagem||'Falha no envio');}await abrir(atual);}catch(e){$('chat-estado-envio').textContent=e.message;}finally{$('chat-anexo').value='';}};
 async function baixar(id){const r=await fetch('/api/autentica/arquivos/'+id,{headers:{Authorization:'Bearer '+localStorage.getItem('assineai-token')}});if(!r.ok)throw Error('Não foi possível baixar o arquivo');const url=URL.createObjectURL(await r.blob()),a=document.createElement('a');a.href=url;const cd=r.headers.get('Content-Disposition')||'';a.download=decodeURIComponent(cd.match(/filename\*=UTF-8''([^;]+)/i)?.[1]||cd.match(/filename="([^"]+)"/)?.[1]||'anexo');a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);}
 $('chat-historico-fechar').onclick=()=>$('chat-historico-dialog').close();
 $('chat-historico').onclick=()=>historico(atual).catch(e=>aviso(e.message,true));
 $('chat-busca').oninput=()=>{clearTimeout(timer);timer=setTimeout(()=>listar(),300);};
 $('chat-atualizar').onclick=()=>listar();
 $('chat-nova').onclick=async()=>{try{opcoes($('chat-novo-canal'),await registros('canais'));opcoes($('chat-novo-contato'),await registros('contatos'));opcoes($('chat-novo-setor'),await registros('setores'));$('chat-nova-dialog').showModal();}catch(e){$('chat-aviso').textContent=e.message;}};$('chat-nova-fechar').onclick=()=>$('chat-nova-dialog').close();
 $('chat-novo-canal').onchange=async()=>opcoes($('chat-novo-setor'),(await registros('setores')).filter(s=>!s.CanalId||s.CanalId===Number($('chat-novo-canal').value)));
 $('chat-nova-form').onsubmit=async e=>{e.preventDefault();try{const a=await api('conversas','POST',{CanalId:Number($('chat-novo-canal').value),ContatoId:Number($('chat-novo-contato').value),SetorId:$('chat-novo-setor').value?Number($('chat-novo-setor').value):null});$('chat-nova-dialog').close();await listar();await abrir(a.id);}catch(erro){$('chat-nova-erro').textContent=erro.message;}};
 desenhar();
 let exportacao=[];
 function linhasExportacao(){const selecionados=new Set(tabela.obterSelecionados());return selecionados.size?tabela.getEstado().todos.filter(a=>selecionados.has(a.id)):tabela.dados();}
 $('exportar-dados').onclick=()=>{
  exportacao=linhasExportacao().map(a=>({...a}));if(!exportacao.length){aviso('Nenhuma conversa para exportar.');return;}
  const n=tabela.obterSelecionados().length;$('chat-exportar-resumo').textContent=exportacao.length+' conversa(s) '+(n?'selecionada(s)':'no resultado dos filtros')+'.';$('chat-exportar-concordo').checked=false;document.querySelectorAll('[data-chat-formato]').forEach(b=>b.disabled=true);$('chat-exportar-dialog').showModal();
 };
 $('chat-exportar-concordo').onchange=e=>document.querySelectorAll('[data-chat-formato]').forEach(b=>b.disabled=!e.target.checked);
 document.querySelectorAll('[data-fechar-dialog]').forEach(b=>b.onclick=()=>b.closest('dialog').close());
 document.querySelectorAll('[data-chat-formato]').forEach(b=>b.onclick=()=>{
  if(!window.FirmaAcesso||!$('chat-exportar-concordo').checked)return;
  const campos=tabela.colunas.filter(c=>tabela.estado.visiveis.includes(c.id)),linhas=exportacao.map(a=>Object.fromEntries(campos.map(c=>[c.nome,tabela.formatado(a,c.id)??''])));
  if(b.dataset.chatFormato==='pdf'){
   const sec=document.createElement('section');sec.className='zapiou-impressao';const titulo=document.createElement('h1');titulo.textContent='Zapiou · Conversas';const table=document.createElement('table'),thead=document.createElement('thead'),tr=document.createElement('tr');for(const c of campos){const th=document.createElement('th');th.textContent=c.nome;tr.append(th);}thead.append(tr);table.append(thead);const tbody=document.createElement('tbody');for(const a of linhas){const row=document.createElement('tr');for(const c of campos){const td=document.createElement('td');td.textContent=a[c.nome];row.append(td);}tbody.append(row);}table.append(tbody);sec.append(titulo,table);document.body.append(sec);try{window.print();}finally{sec.remove();}
  }else{
   const csv=v=>'"'+String(v).replace(/^[=+@\-\t\r]/,"'$&").replaceAll('"','""')+'"';
   const conteudo=b.dataset.chatFormato==='json'?JSON.stringify(linhas,null,2):'\uFEFF'+[campos.map(c=>csv(c.nome)).join(';'),...linhas.map(a=>campos.map(c=>csv(a[c.nome])).join(';'))].join('\r\n');
   const url=URL.createObjectURL(new Blob([conteudo],{type:b.dataset.chatFormato==='json'?'application/json;charset=utf-8':'text/csv;charset=utf-8'})),link=document.createElement('a');link.href=url;link.download='zapiou-conversas.'+b.dataset.chatFormato;link.click();setTimeout(()=>URL.revokeObjectURL(url),10000);
  }
  $('chat-exportar-dialog').close();
 });
 $('importar-dados').onclick=async()=>{try{const canais=(await registros('canais')).filter(c=>c.CanalProvedorAPI==='Baileys');if(!canais.length){aviso('Cadastre e conecte um canal WhatsApp para importar conversas.');return;}opcoes($('chat-importar-canal'),canais);$('chat-importar-dialog').showModal();}catch(e){aviso(e.message,true);}};
 $('chat-importar-form').onsubmit=async e=>{e.preventDefault();const b=e.target.querySelector('[type=submit]');b.disabled=true;try{await api('canais/'+$('chat-importar-canal').value+'/importar','POST');$('chat-importar-dialog').close();await listar();aviso('Importação solicitada. As conversas serão atualizadas durante a sincronização.');}catch(erro){aviso(erro.message,true);}finally{b.disabled=false;}};
 function acessoAtivo(ativo){for(const id of ['chat-nova','chat-atualizar','chat-expandir','chat-busca','exportar-dados','importar-dados'])$(id).disabled=!ativo;if(ativo)$('importar-dados').disabled=!contexto?.modulos.find(m=>m.slug==='canais')?.acoes?.includes('importar');}
 acessoAtivo(false);
 async function iniciar(c){contexto=c;caches={};tabela.storageId='zapiou-'+c.clienteId+'-'+c.usuarioId;tabela.restaurar();$('chat-aviso').textContent='';acessoAtivo(true);await listar();}
 document.addEventListener('firma:contexto',e=>iniciar(e.detail));
 document.addEventListener('firma:desconectado',()=>{versaoLista++;versao++;contexto=null;exportacao=[];document.querySelectorAll('dialog[open]').forEach(d=>d.close());atual=null;conversaAtual=null;resposta=null;todos=[];caches={};aberta=false;integral=false;document.body.classList.remove('zapiou-integral');$('chat-expandir').textContent='EXPANDIR CHAT ↗';$('chat-texto').value='';$('chat-conversa').hidden=true;$('chat-vazio').hidden=false;acessoAtivo(false);tabela?.limparDados();if(tabela){tabela.estado.salvos=[];tabela.storageId='zapiou-desconectado';}desenhar();$('chat-aviso').textContent='Conecte o token no rodapé para carregar as conversas.';});
 // Atualização em segundo plano preserva rascunhos e somente consulta dados.
 const poll=setInterval(()=>{if(contexto&&!document.hidden){caches={};listar().then(()=>atual&&abrir(atual)).catch(()=>{});}},15000);window.addEventListener('pagehide',()=>{clearInterval(poll);observadorChat?.disconnect();});
})();
