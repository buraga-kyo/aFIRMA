(() => {
  const $ = id => document.getElementById(id);
  const colunas = [
    {id:'id', nome:'ID', tipo:'texto'}, {id:'titulo', nome:'TÍTULO', tipo:'texto'},
    {id:'data', nome:'DATA', tipo:'data'}, {id:'responsavel', nome:'RESPONSÁVEL', tipo:'texto'},
    {id:'status', nome:'STATUS', tipo:'enum'}, {id:'quantidadeAssinaturas', nome:'ASSINATURAS', tipo:'numero'},
    {id:'signatarios', nome:'SIGNATÁRIOS', tipo:'texto'}
  ];
  const padrao = colunas.map(c => c.id);
  const largurasPadrao = {acoes:96,id:150,titulo:230,data:160,responsavel:180,status:145,quantidadeAssinaturas:150,signatarios:250};
  const estado = {todos:[], visiveis:[...padrao], rascunho:[...padrao], filtros:{}, ordem:null, pagina:1,
    tamanho:10, larguras:{...largurasPadrao}, editando:null, tituloOriginal:'', aviso:null, plano:null, ia:[], salvos:[], credenciais:null, aberto:null};
  let menuColunaAberto=null;
  try{const salvas=JSON.parse(localStorage.getItem('assineai-larguras-documentos')||'{}');Object.keys(largurasPadrao).forEach(id=>{if(Number.isFinite(salvas[id]))estado.larguras[id]=Math.max(id==='acoes'?86:80,Math.min(600,salvas[id]))})}catch{}
  const data = valor => valor ? new Intl.DateTimeFormat('pt-BR',{dateStyle:'short',timeStyle:'short'}).format(new Date(valor)) : '—';
  const texto = (elemento, valor) => { elemento.textContent = String(valor ?? ''); return elemento; };
  const el = (tag, classe, valor) => {const node=document.createElement(tag); if(classe)node.className=classe; if(valor!==undefined)texto(node,valor); return node;};
  const botao = (rotulo, funcao, classe='') => {const b=el('button',classe,rotulo);b.type='button';b.addEventListener('click',funcao);return b;};
  const valor = (item,id) => id==='signatarios' ? item.signatarios.map(s=>s.nome).join(', ') : id==='data' ? data(item.data) : item[id];
  const credenciais = () => estado.credenciais ? {'X-API-Key':estado.credenciais.chave, Authorization:`Bearer ${estado.credenciais.token}`} : {};
  async function api(path, options={}) {
    const resposta=await fetch(`/api/envelopes/documentos${path}`,{...options,headers:{...credenciais(),...(options.body?{'Content-Type':'application/json'}:{}),...options.headers}});
    if(!resposta.ok){let detalhe={};try{detalhe=await resposta.json()}catch{}throw new Error(resposta.status===401||resposta.status===403?'Credenciais inválidas ou sem permissão.':detalhe.mensagem||`Erro ${resposta.status} na operação.`);}
    return resposta.status===204||options.method==='DELETE'?null:resposta.json();
  }
  function mensagem(msg,erro=false){$('lista-acesso-estado').textContent=msg;$('lista-acesso-estado').style.color=erro?'var(--accent)':'';}
  async function carregar(){
    $('tabela-estado').textContent='Carregando documentos…';
    try{estado.todos=await api('');mensagem(`${estado.todos.length} documento(s) carregado(s).`);render();}
    catch(e){$('tabela-estado').textContent=e.message; mensagem(e.message,true);}
  }
  function fecharMenus(){document.querySelectorAll('.menu').forEach(m=>m.hidden=true);document.querySelectorAll('[aria-expanded]').forEach(b=>b.setAttribute('aria-expanded','false'));estado.aberto=null;}
  function abrir(id,botaoId){const painel=$(id),aberto=!painel.hidden;fecharMenus();if(!aberto){painel.hidden=false;$(botaoId).setAttribute('aria-expanded','true');estado.aberto=id;if(id==='menu-colunas')posicionarMenuColunas();}}
  function posicionarMenuColunas(){const menu=$('menu-colunas');if(menu.hidden)return;const ancora=$('colunas-botao').getBoundingClientRect(),largura=menu.offsetWidth;menu.style.left=`${Math.max(12,Math.min(ancora.left,innerWidth-largura-12))}px`;menu.style.top=`${ancora.bottom+6}px`;menu.style.maxHeight=`${Math.max(120,innerHeight-ancora.bottom-18)}px`;}
  function corresponde(item,filtros){
    return Object.entries(filtros).every(([id,f])=>{
      if(!f)return true;const atual=String(valor(item,id)??'');
      if(Array.isArray(f))return !f.length||f.includes(atual);
      if(f.from!==undefined||f.to!==undefined){const n=id==='data'?new Date(item.data).getTime():Number(item[id]);const de=id==='data'?new Date(f.from).getTime():Number(f.from);const ate=id==='data'?new Date(f.to).getTime():Number(f.to);return (!f.from||n>=de)&&(!f.to||n<=ate);}
      return atual.toLocaleLowerCase('pt-BR').includes(String(f).toLocaleLowerCase('pt-BR'));
    });
  }
  function dados(){
    let itens=estado.todos.filter(item=>corresponde(item,estado.filtros));
    if(estado.ordem){const {id,dir}=estado.ordem;itens.sort((a,b)=>{let x=id==='data'?new Date(a.data).getTime():valor(a,id),y=id==='data'?new Date(b.data).getTime():valor(b,id);return (typeof x==='number'?x-y:String(x).localeCompare(String(y),'pt-BR',{numeric:true}))*(dir==='asc'?1:-1);});}
    return itens;
  }
  function aplicarLarguras(){
    const ids=['acoes',...estado.visiveis], grupo=$('tabela-colunas');grupo.replaceChildren();
    ids.forEach(id=>{const coluna=el('col');coluna.dataset.colunaId=id;coluna.style.width=`${estado.larguras[id]}px`;grupo.append(coluna)});
    $('tabela-documentos').style.width=`${ids.reduce((s,id)=>s+estado.larguras[id],0)}px`;
  }
  function alcaColuna(id,nome){
    const alca=el('span','alca-coluna');alca.tabIndex=0;alca.setAttribute('role','separator');alca.setAttribute('aria-orientation','vertical');alca.setAttribute('aria-label',`Redimensionar ${nome}`);alca.setAttribute('aria-valuemin','80');alca.setAttribute('aria-valuemax','600');
    const mudar=largura=>{estado.larguras[id]=Math.max(id==='acoes'?86:80,Math.min(600,largura));const coluna=[...$('tabela-colunas').children].find(c=>c.dataset.colunaId===id);if(coluna)coluna.style.width=`${estado.larguras[id]}px`;$('tabela-documentos').style.width=`${['acoes',...estado.visiveis].reduce((s,chave)=>s+estado.larguras[chave],0)}px`;alca.setAttribute('aria-valuenow',String(estado.larguras[id]));};
    alca.setAttribute('aria-valuenow',String(estado.larguras[id]));
    alca.addEventListener('pointerdown',evento=>{evento.preventDefault();evento.stopPropagation();fecharMenuColuna();alca.setPointerCapture(evento.pointerId);const inicio=evento.clientX,largura=estado.larguras[id];const mover=e=>mudar(largura+e.clientX-inicio);const parar=()=>{alca.removeEventListener('pointermove',mover);alca.removeEventListener('pointerup',parar);alca.removeEventListener('pointercancel',parar);localStorage.setItem('assineai-larguras-documentos',JSON.stringify(estado.larguras))};alca.addEventListener('pointermove',mover);alca.addEventListener('pointerup',parar);alca.addEventListener('pointercancel',parar)});
    alca.addEventListener('keydown',evento=>{if(evento.key!=='ArrowLeft'&&evento.key!=='ArrowRight')return;evento.preventDefault();mudar(estado.larguras[id]+(evento.key==='ArrowRight'?10:-10));localStorage.setItem('assineai-larguras-documentos',JSON.stringify(estado.larguras))});
    return alca;
  }
  function render(){
    fecharMenuColuna();
    const chips=$('chips-filtros');chips.replaceChildren();
    Object.entries(estado.filtros).forEach(([id,f])=>{if(!f||(Array.isArray(f)&&!f.length))return;const chip=el('span','chip',`${colunas.find(c=>c.id===id)?.nome}: ${Array.isArray(f)?f.join(', '):typeof f==='object'?`${f.from||'…'}–${f.to||'…'}`:f}`);chip.append(botao('✕',()=>{delete estado.filtros[id];estado.pagina=1;render()},'icone'));chips.append(chip);});
    if(estado.ia.length){const chip=el('span','chip',`⌁ ${estado.ia.map(x=>x.texto).join('; ')}`);chip.append(botao('✕',()=>{estado.ia.forEach(x=>x.desfazer());estado.ia=[];render()},'icone'));chips.append(chip);}
    aplicarLarguras();
    const head=$('tabela-cabecalho');head.replaceChildren();const acoesTitulo=el('th');acoesTitulo.append(el('span','titulo-coluna titulo-coluna-fixa','AÇÕES'),alcaColuna('acoes','ações'));head.append(acoesTitulo);
    estado.visiveis.forEach(id=>{const c=colunas.find(x=>x.id===id),th=el('th');const titulo=botao(`${c.nome}${estado.ordem?.id===id?(estado.ordem.dir==='asc'?' ▲':' ▼'):''}`,()=>menuColuna(c,titulo),'titulo-coluna');titulo.setAttribute('aria-label',`Opções da coluna ${c.nome}`);titulo.setAttribute('aria-expanded','false');if(estado.filtros[id])titulo.classList.add('filtrada');th.append(titulo,alcaColuna(id,c.nome));head.append(th);});
    const itens=dados(),total=Math.max(1,Math.ceil(itens.length/estado.tamanho));estado.pagina=Math.min(estado.pagina,total);
    const corpo=$('tabela-corpo');corpo.replaceChildren();itens.slice((estado.pagina-1)*estado.tamanho,estado.pagina*estado.tamanho).forEach(item=>corpo.append(linha(item)));
    $('tabela-estado').textContent=!estado.credenciais?'Conecte para carregar documentos.':!estado.todos.length?'Nenhum documento cadastrado.':!itens.length?'Nenhum resultado para os filtros atuais. Use ⊘ para limpar.':'';
    $('pagina-texto').textContent=`PAGE ${estado.pagina} OF ${total} · ${estado.tamanho} LINHAS ▾`;
    $('menu-pagina').hidden=true;$('pagina-texto').setAttribute('aria-expanded','false');
    const pag=$('paginacao');pag.replaceChildren();const anterior=botao('←',()=>{estado.pagina--;render()});anterior.disabled=estado.pagina===1;pag.append(anterior);
    let inicio=Math.max(1,Math.min(estado.pagina-2,total-4));for(let p=inicio;p<=Math.min(total,inicio+4);p++)pag.append(botao(String(p),()=>{estado.pagina=p;render()},p===estado.pagina?'atual':''));
    const proximo=botao('→',()=>{estado.pagina++;render()});proximo.disabled=estado.pagina===total;pag.append(proximo);
  }
  function linha(item){
    const tr=el('tr');const acoes=el('td');
    if(estado.editando===item.id){const input=el('input');input.value=estado.tituloOriginal;input.maxLength=255;input.setAttribute('aria-label','Título do documento');input.addEventListener('input',()=>input.classList.toggle('alterado',input.value!==estado.tituloOriginal));
      acoes.append(botao('✓',async()=>{const titulo=input.value.trim();if(!titulo){input.style.borderColor='var(--accent)';return;}try{await api(`/${item.id}`,{method:'PATCH',body:JSON.stringify({titulo})});estado.editando=null;await carregar();}catch(e){toast(e.message);}},'icone'),botao('✕',()=>{estado.editando=null;render()},'icone'));
      tr._tituloInput=input;
    }else acoes.append(botao('✎',()=>editar(item),'icone'),botao('✖',()=>{if(confirm(`Excluir “${item.titulo}”?`))api(`/${item.id}`,{method:'DELETE'}).then(carregar).catch(e=>toast(e.message))},'icone destrutivo'));
    tr.append(acoes);
    estado.visiveis.forEach(id=>{const td=el('td');td.title=String(valor(item,id)??'');if(id==='titulo'&&estado.editando===item.id)td.append(tr._tituloInput);else if(id==='titulo'){const a=el('a','',item.titulo);a.href='#';a.title=item.titulo;a.addEventListener('click',async e=>{e.preventDefault();try{const resposta=await fetch(`/api/envelopes/${item.id}/pdf`,{headers:credenciais()});if(!resposta.ok)throw new Error(`Erro ${resposta.status} ao baixar PDF`);const url=URL.createObjectURL(await resposta.blob()),link=el('a');link.href=url;link.download=item.titulo.toLowerCase().endsWith('.pdf')?item.titulo:`${item.titulo}.pdf`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}catch(erro){toast(erro.message)}});td.append(a);estado.salvos.filter(s=>s.ativo&&corresponde(item,s.filtros)).forEach(s=>{const badge=el('span','chip',s.nome);badge.style.borderColor=s.cor;badge.style.color=s.cor;td.append(badge)})}else if(id==='status'){td.append(el('span','chip',item.status));}else td.textContent=String(valor(item,id)??'—');tr.append(td);});
    tr.addEventListener('click',e=>{if(e.target.closest('button,a,input')||window.getSelection()?.toString())return;editar(item)});
    return tr;
  }
  function editar(item){if(!estado.credenciais)return;if(estado.editando&&estado.editando!==item.id){const input=document.querySelector('tr input[aria-label="Título do documento"]');if(input&&input.value!==estado.tituloOriginal){toast('Descartar alterações?',()=>{estado.editando=item.id;estado.tituloOriginal=item.titulo;render()});return;}}
    estado.editando=item.id;estado.tituloOriginal=item.titulo;render();document.querySelector('tr input[aria-label="Título do documento"]')?.focus();}
  function toast(msg,descartar){const box=$('lista-toast');box.replaceChildren(el('span','',msg));if(descartar)box.append(botao('✓ DESCARTAR',()=>{box.hidden=true;descartar()}),botao('✕ CONTINUAR EDITANDO',()=>box.hidden=true));else setTimeout(()=>box.hidden=true,4500);box.hidden=false;}
  function fecharMenuColuna(){if(!menuColunaAberto)return;menuColunaAberto.ancora.setAttribute('aria-expanded','false');menuColunaAberto.menu.remove();menuColunaAberto=null;}
  function posicionarMenuColuna(){if(!menuColunaAberto)return;const {menu,ancora}=menuColunaAberto,caixa=ancora.getBoundingClientRect();if(caixa.bottom<0||caixa.top>innerHeight){fecharMenuColuna();return;}const largura=Math.min(280,innerWidth-24),abaixo=innerHeight-caixa.bottom-18,acima=caixa.top-18;menu.style.width=`${largura}px`;menu.style.left=`${Math.max(12,Math.min(caixa.left,innerWidth-largura-12))}px`;if(abaixo<Math.min(menu.scrollHeight,320)&&acima>abaixo){const altura=Math.min(menu.scrollHeight,acima);menu.style.top=`${Math.max(12,caixa.top-altura-6)}px`;menu.style.maxHeight=`${Math.max(80,acima)}px`;}else{menu.style.top=`${caixa.bottom+6}px`;menu.style.maxHeight=`${Math.max(80,abaixo)}px`;}}
  function menuColuna(col,ancora){if(menuColunaAberto?.ancora===ancora){fecharMenuColuna();return;}fecharMenuColuna();const menu=el('div','menu coluna-menu');menu.setAttribute('role','dialog');menu.setAttribute('aria-label',`Opções da coluna ${col.nome}`);const ordenar=(dir,label)=>menu.append(botao(label,()=>{estado.ordem={id:col.id,dir};render()}));ordenar('asc',col.tipo==='numero'?'Menor → maior':col.tipo==='data'?'Antigo → recente':'A → Z');ordenar('desc',col.tipo==='numero'?'Maior → menor':col.tipo==='data'?'Recente → antigo':'Z → A');menu.append(el('hr'));
    menu.append(botao('◧ Fix to the left',()=>fixar(col.id,true)),botao('◨ Fix to the right',()=>fixar(col.id,false)),el('hr'));
    menu.append(el('p','', 'FILTROS SALVOS'));const salvos=estado.salvos.filter(s=>s.coluna===col.id);if(!salvos.length)menu.append(el('p','', '(Nenhum filtro salvo)'));salvos.forEach(s=>{const label=el('label'),check=el('input');check.type='checkbox';check.checked=Boolean(s.ativo);check.addEventListener('change',()=>{s.ativo=check.checked;estado.filtros[col.id]=check.checked?structuredClone(s.filtros[col.id]):undefined;persistirSalvos();render()});label.append(check,el('span','',`● ${s.nome}`));label.style.color=s.cor;menu.append(label)});menu.append(botao('▣ Salvar filtro atual',()=>salvarFiltro(col.id)),el('hr'));
    if(['numero','data'].includes(col.tipo)){const de=el('input'),ate=el('input');de.type=col.tipo==='data'?'date':'number';ate.type=de.type;de.placeholder='From';ate.placeholder='To';const f=estado.filtros[col.id]||{};de.value=f.from||'';ate.value=f.to||'';menu.append(de,ate,botao('⌕ SEARCH',()=>{estado.filtros[col.id]={from:de.value,to:ate.value};estado.pagina=1;menu.remove();render()}));}
    else {const busca=el('input');busca.placeholder='⌕ Buscar valores';menu.append(busca);const lista=el('div','valor-lista');menu.append(lista);const preencher=()=>{lista.replaceChildren();const contagens=new Map();estado.todos.forEach(item=>{const v=String(valor(item,col.id)??'');contagens.set(v,(contagens.get(v)||0)+1)});[...contagens].filter(([v])=>v.toLowerCase().includes(busca.value.toLowerCase())).sort((a,b)=>a[0].localeCompare(b[0])).forEach(([v,n])=>{const label=el('label'),check=el('input');check.type='checkbox';check.checked=(estado.filtros[col.id]||[]).includes(v);check.addEventListener('change',()=>{const s=new Set(estado.filtros[col.id]||[]);check.checked?s.add(v):s.delete(v);estado.filtros[col.id]=[...s];estado.pagina=1;render()});label.append(check,el('span','',`${v} (${n})`));lista.append(label)});};busca.addEventListener('input',preencher);preencher();}
    menu.append(el('hr'),botao('⊘ Limpar todos os filtros',()=>{estado.filtros={};estado.ia=[];estado.pagina=1;estado.salvos.forEach(s=>s.ativo=false);persistirSalvos();render()}));
    document.body.append(menu);menuColunaAberto={menu,ancora};ancora.setAttribute('aria-expanded','true');posicionarMenuColuna();}
  function fixar(id,esquerda){estado.visiveis=estado.visiveis.filter(x=>x!==id);esquerda?estado.visiveis.unshift(id):estado.visiveis.push(id);render();}
  function menuColunas(){const menu=$('menu-colunas');menu.replaceChildren();const busca=el('input');busca.placeholder='⌕ Buscar colunas';menu.append(busca);const grupo=el('div');menu.append(grupo);function preencher(){grupo.replaceChildren();colunas.filter(c=>c.nome.toLowerCase().includes(busca.value.toLowerCase())).forEach(c=>{const label=el('label'),check=el('input');check.type='checkbox';check.checked=estado.rascunho.includes(c.id);check.addEventListener('change',()=>{estado.rascunho=check.checked?[...estado.rascunho,c.id]:estado.rascunho.filter(x=>x!==c.id)});label.append(check,el('span','',c.nome));grupo.append(label)});}busca.addEventListener('input',preencher);preencher();menu.append(el('hr'),botao('↺',()=>{estado.rascunho=[...padrao];preencher()}),botao('✓ UPDATE',()=>{estado.visiveis=padrao.filter(id=>estado.rascunho.includes(id));fecharMenus();render()},'primaria'));}
  function chaveSalvos(){try{const payload=JSON.parse(atob(estado.credenciais.token.split('.')[1].replaceAll('-','+').replaceAll('_','/')));return `assineai-filtros-${payload.sub||'usuario'}`}catch{return 'assineai-filtros-local'}}
  function persistirSalvos(){localStorage.setItem(chaveSalvos(),JSON.stringify(estado.salvos))}
  $('pagina-texto').addEventListener('click',()=>{
    const menu=$('menu-pagina'),aberto=!menu.hidden;menu.replaceChildren();
    if(aberto){menu.hidden=true;$('pagina-texto').setAttribute('aria-expanded','false');return;}
    menu.append(el('p','', 'LINHAS POR PÁGINA'));
    const tamanhos=el('div','tamanhos-pagina');[5,10,50,100].forEach(n=>tamanhos.append(botao(String(n),()=>{estado.tamanho=n;estado.pagina=1;render()},n===estado.tamanho?'atual':'')));menu.append(tamanhos);
    menu.append(el('hr'),el('p','', 'IR PARA A PÁGINA'));
    const salto=el('div','salto-pagina'),numero=el('input');numero.type='number';numero.min='1';numero.max=String(Math.max(1,Math.ceil(dados().length/estado.tamanho)));numero.value=String(estado.pagina);numero.setAttribute('aria-label','Número da página');salto.append(numero,botao('IR',()=>{estado.pagina=Math.min(Number(numero.max),Math.max(1,Number(numero.value)||1));render()}));menu.append(salto);
    menu.hidden=false;$('pagina-texto').setAttribute('aria-expanded','true');numero.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();salto.querySelector('button').click()}});
  });
  function salvarFiltro(coluna){if(!estado.filtros[coluna]){toast('Crie um filtro nesta coluna antes de salvar.');return;}const nome=prompt('Nome do filtro:');if(!nome?.trim())return;const cores=['#C9FF3D','#E8792B','#A9C2AC'];estado.salvos.push({nome:nome.trim().slice(0,40),coluna,cor:cores[estado.salvos.length%cores.length],filtros:{[coluna]:structuredClone(estado.filtros[coluna])},ativo:true});persistirSalvos();render()}
  $('lista-conectar').addEventListener('click',()=>{const chave=$('lista-chave').value.trim(),token=$('lista-token').value.trim();if(!chave||!token){mensagem('Informe chave e token.',true);return;}estado.credenciais={chave,token};try{estado.salvos=JSON.parse(localStorage.getItem(chaveSalvos())||'[]')}catch{estado.salvos=[]}carregar();});
  $('colunas-botao').addEventListener('click',()=>{estado.rascunho=[...estado.visiveis];menuColunas();abrir('menu-colunas','colunas-botao')});
  $('exportar-excel').addEventListener('click',()=>{const linhas=[estado.visiveis.map(id=>colunas.find(c=>c.id===id).nome),...dados().map(item=>estado.visiveis.map(id=>String(valor(item,id)??'')))];const csv='\ufeff'+linhas.map(l=>l.map(x=>`"${x.replaceAll('"','""')}"`).join(';')).join('\r\n');const a=el('a');a.href=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));a.download='documentos-assinatura.csv';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)});
  $('exportar-pdf').addEventListener('click',()=>window.print());['importar-excel','importar-csv'].forEach(id=>$(id).addEventListener('click',()=>toast('Importação requer PDFs e signatários. Use + INSERT para cadastrar um documento.')));
  $('ia-form').addEventListener('submit',async e=>{
    e.preventDefault();const consulta=$('ia-consulta').value.trim();if(!consulta)return;
    const painel=$('plano-ia');painel.hidden=false;painel.replaceChildren(el('p','', 'Interpretando…'));
    try{const plano=await api('/plano-busca',{method:'POST',body:JSON.stringify({consulta})});estado.plano=plano;mostrarPlano(consulta);}
    catch(erro){painel.replaceChildren(el('p','',erro.message),el('p','', 'Tente status, data ou responsável.'));painel.style.borderColor='var(--accent)';}
  });
  function mostrarPlano(consulta){const painel=$('plano-ia');painel.style.borderColor='var(--border)';painel.replaceChildren(el('h2','', 'PLANO DE BUSCA — IA'),el('p','',estado.plano.summary||consulta));
    const lista=el('ol');estado.plano.steps.forEach((etapa,i)=>{const item=el('li'),descricao=etapa.op==='sort'?`Ordenar ${etapa.column} ${etapa.dir}`:`Filtrar ${etapa.column}: ${etapa.type==='range'?`${etapa.from||'…'} até ${etapa.to||'…'}`:etapa.value}`;item.append(el('span','plano-numero',`${i+1}. `),el('span','',descricao),botao('✕',()=>{estado.plano.steps.splice(i,1);mostrarPlano(consulta)},'icone'));lista.append(item)});painel.append(lista,el('p','',`≈ ${estimar(estado.plano.steps)} resultados`),botao('✓ APLICAR',()=>aplicarPlano(consulta),'primaria'),botao('✕ CANCELAR',()=>{painel.hidden=true;estado.plano=null}));}
  function estimar(etapas){return estado.todos.filter(item=>etapas.every(e=>{if(e.op!=='column-filter')return true;const v=String(e.column==='data'?item.data:valor(item,e.column)??'').toLowerCase();return e.type==='equals'?v===e.value.toLowerCase():e.type==='contains'?v.includes(e.value.toLowerCase()):(!e.from||v>=e.from)&&(!e.to||v<=e.to)})).length;}
  function aplicarPlano(consulta){
    if(!estado.plano)return;estado.ia.forEach(x=>x.desfazer());estado.ia=[];
    const anteriores=new Map(),aplicados=new Map(),ordemAnterior=estado.ordem?{...estado.ordem}:null;
    estado.plano.steps.forEach(e=>{if(e.op==='sort')estado.ordem={id:e.column,dir:e.dir};else{if(!anteriores.has(e.column))anteriores.set(e.column,structuredClone(estado.filtros[e.column]??null));const novo=e.type==='range'?{from:e.from,to:e.to}:e.type==='equals'?[e.value]:e.value;estado.filtros[e.column]=novo;aplicados.set(e.column,structuredClone(novo));}});
    const ordemAplicada=estado.ordem?{...estado.ordem}:null;
    estado.ia=[{texto:consulta,desfazer:()=>{anteriores.forEach((f,id)=>{if(JSON.stringify(estado.filtros[id])===JSON.stringify(aplicados.get(id))){if(f===null)delete estado.filtros[id];else estado.filtros[id]=f;}});if(JSON.stringify(estado.ordem)===JSON.stringify(ordemAplicada))estado.ordem=ordemAnterior;}}];estado.pagina=1;$('plano-ia').hidden=true;estado.plano=null;render();
  }
  document.addEventListener('pointerdown',e=>{if(menuColunaAberto&&!menuColunaAberto.menu.contains(e.target)&&!menuColunaAberto.ancora.contains(e.target))fecharMenuColuna();if(!$('menu-pagina').hidden&&!$('menu-pagina').contains(e.target)&&!$('pagina-texto').contains(e.target)){$('menu-pagina').hidden=true;$('pagina-texto').setAttribute('aria-expanded','false')}});
  window.addEventListener('resize',()=>{posicionarMenuColuna();posicionarMenuColunas()});
  window.addEventListener('scroll',()=>{posicionarMenuColuna();posicionarMenuColunas()},true);
  document.addEventListener('keydown',e=>{if(e.key==='Escape'){if(estado.editando){estado.editando=null;render()}fecharMenuColuna();$('menu-pagina').hidden=true;$('pagina-texto').setAttribute('aria-expanded','false');fecharMenus()}});
  const modosLargura=[['total','TELA INTEIRA'],['largo','80% DA TELA'],['centro','CENTRO']];
  function aplicarModoLargura(modo){const atual=modosLargura.findIndex(([id])=>id===modo),indice=atual<0?0:atual; $('pagina-documentos').dataset.largura=modosLargura[indice][0];$('alternar-largura').textContent=`▤ ${modosLargura[indice][1]}`;$('alternar-largura').setAttribute('aria-label',`Largura ${modosLargura[indice][1]}. Clique para ${modosLargura[(indice+1)%3][1]}.`);localStorage.setItem('assineai-largura-documentos-v2',modosLargura[indice][0]);}
  $('alternar-largura').addEventListener('click',()=>{const atual=modosLargura.findIndex(([id])=>id===$('pagina-documentos').dataset.largura);aplicarModoLargura(modosLargura[(atual+1)%3][0])});
  aplicarModoLargura(localStorage.getItem('assineai-largura-documentos-v2')||'total');
  render();
})();
