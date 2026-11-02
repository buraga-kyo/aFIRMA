(() => {
  const $=id=>document.getElementById(id), clonar=v=>JSON.parse(JSON.stringify(v));
  const rotulos={cabecalho:'Identidade e título',documento:'Dados do documento',signatarios:'Dados dos signatários',integridade:'Integridade e emissão'};
  let biblioteca, selecionado, catalogo, pendente=false, timer, versao=0, urlPrevia;
  const historico=[], futuro=[], urls=[];
  const atual=()=>biblioteca.modelos.find(m=>m.id===selecionado);
  const estado=t=>$('template-estado').textContent=t;
  async function api(caminho,dados) {
    const r=await fetch('/api/assineai/empresa/templates'+caminho,{method:dados?'POST':'GET',cache:'no-store',headers:{Authorization:`Bearer ${$('empresa-token').value.trim()}`,...(dados?{'Content-Type':'application/json'}:{})},...(dados?{body:JSON.stringify(dados)}:{})});
    if(!r.ok){const e=await r.json().catch(()=>({}));throw Error(e.mensagem||`Não foi possível renderizar a folha (${r.status}).`);}return r;
  }
  function marcar(){pendente=true;estado('Alterações em edição. Salve para aplicar às próximas gerações do PDF.');}
  function guardar(){historico.push(clonar(atual()));if(historico.length>60)historico.shift();futuro.length=0;}
  function lista(){const sel=$('template-selecionado');sel.replaceChildren();for(const m of biblioteca.modelos){const o=document.createElement('option');o.value=m.id;o.textContent=m.nome+(m.id===biblioteca.ativo?' · PADRÃO':'');sel.append(o);}sel.value=selecionado;
    $('template-ativo').textContent='Padrão da empresa: '+biblioteca.modelos.find(m=>m.id===biblioteca.ativo).nome;
    document.querySelectorAll('.registro-card').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.design===atual().design)));
  }
  function preencher(){lista();document.querySelectorAll('[data-modelo]').forEach(e=>e.value=atual()[e.dataset.modelo]??(e.dataset.modelo==='opacidade'?0.08:''));document.querySelectorAll('[data-cor]').forEach(e=>e.value=atual().cores[e.dataset.cor]);medidas();alcas();agendar();}
  function medidas(){const a=atual().areas[$('template-area').value];document.querySelectorAll('[data-area]').forEach(e=>e.value=a[e.dataset.area]);}
  function agendar(){clearTimeout(timer);timer=setTimeout(()=>previa().catch(e=>estado(e.message)),650);}
  async function previa(){const minhaVersao=++versao;const r=await api('/previa',{modelo:clonar(atual()),empresa:$('perfil-nome').value.trim()||'Sua empresa',formato:'png'});const url=URL.createObjectURL(await r.blob());if(minhaVersao!==versao){URL.revokeObjectURL(url);return;}if(urlPrevia)URL.revokeObjectURL(urlPrevia);urlPrevia=url;$('template-previa').src=url;alcas();}
  function alcas(){const camada=$('template-alcas');camada.replaceChildren();camada.hidden=!$('template-editar-posicoes').checked;
    for(const [id,a] of Object.entries(atual().areas)){
      const el=document.createElement('div');el.className='registro-alca'+(id===$('template-area').value?' selecionada':'');el.dataset.bloco=id;el.tabIndex=0;el.setAttribute('role','button');el.setAttribute('aria-label',`Mover ${rotulos[id]}. Use as setas do teclado.`);
      const label=document.createElement('span');label.textContent=rotulos[id];const resize=document.createElement('i');el.append(label,resize);posicionar(el,a);camada.append(el);
      el.onpointerdown=e=>{
        e.preventDefault();$('template-area').value=id;medidas();guardar();
        const inicio={...a},x=e.clientX,y=e.clientY,escala=$('template-canvas').getBoundingClientRect().width/595,redimensionar=e.target===resize;
        el.setPointerCapture(e.pointerId);
        el.onpointermove=ev=>{const dx=(ev.clientX-x)/escala,dy=(ev.clientY-y)/escala;
          if(redimensionar){a.w=Math.round(Math.max(400,Math.min(575-a.x,inicio.w+dx)));a.h=Math.round(Math.max(id==='signatarios'?180:id==='documento'?130:id==='cabecalho'?110:60,Math.min(815-a.y,inicio.h+dy)));}
          else {a.x=Math.round(Math.max(20,Math.min(575-a.w,inicio.x+dx)));a.y=Math.round(Math.max(20,Math.min(815-a.h,inicio.y+dy)));}
          posicionar(el,a);medidas();};
        el.onpointerup=()=>{el.onpointermove=null;marcar();agendar();};el.onpointercancel=el.onpointerup;
      };
      el.onkeydown=e=>{if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key))return;e.preventDefault();guardar();const n=e.shiftKey?10:1;a.x=Math.max(20,Math.min(575-a.w,a.x+(e.key==='ArrowRight'?n:e.key==='ArrowLeft'?-n:0)));a.y=Math.max(20,Math.min(815-a.h,a.y+(e.key==='ArrowDown'?n:e.key==='ArrowUp'?-n:0)));$('template-area').value=id;medidas();posicionar(el,a);marcar();agendar();};
    }
  }
  function posicionar(el,a){el.style.left=`${a.x/595*100}%`;el.style.top=`${a.y/842*100}%`;el.style.width=`${a.w/595*100}%`;el.style.height=`${a.h/842*100}%`;}
  async function galeria(){const area=$('modelos-galeria');area.replaceChildren();
    for(const m of catalogo){const botao=document.createElement('button');botao.type='button';botao.className='registro-card';botao.dataset.design=m.design;
      const amostra=document.createElement('div');amostra.className='registro-thumb';amostra.style.background=m.cores.secundaria;
      const img=document.createElement('img');img.alt=`Folha de assinaturas: ${m.nome}`;amostra.append(img);
      const nome=document.createElement('strong');nome.textContent=m.nome.split(' / ')[0];const desc=document.createElement('p');desc.textContent=m.descricao;botao.append(amostra,nome,desc);area.append(botao);
      botao.onclick=()=>{let modelo=biblioteca.modelos.find(v=>v.id===m.id);if(!modelo){if(biblioteca.modelos.length>=24)return estado('Limite de 24 versões. Exclua uma versão antes de adicionar outra.');modelo=clonar(m);biblioteca.modelos.push(modelo);}selecionado=modelo.id;historico.length=0;futuro.length=0;preencher();estado('Modelo selecionado para edição. Defina como padrão e salve para usar no PDF.');};
      try{const r=await api('/previa',{modelo:m,empresa:'Sua empresa',formato:'png'});const url=URL.createObjectURL(await r.blob());urls.push(url);img.src=url;}catch(e){img.alt=e.message;}
    }lista();
  }
  document.querySelectorAll('[data-modelo],[data-cor],[data-area]').forEach(el=>el.oninput=()=>{if(!biblioteca)return;guardar();if(el.dataset.modelo)atual()[el.dataset.modelo]=el.type==='number'?Number(el.value):el.value;else if(el.dataset.cor)atual().cores[el.dataset.cor]=el.value;else atual().areas[$('template-area').value][el.dataset.area]=Number(el.value);marcar();lista();alcas();agendar();});
  $('template-selecionado').onchange=e=>{selecionado=e.target.value;historico.length=0;futuro.length=0;preencher();};
  $('template-area').onchange=()=>{medidas();alcas();};$('template-editar-posicoes').onchange=alcas;
  $('perfil-nome').addEventListener('input',()=>biblioteca&&agendar());
  $('template-ativar').onclick=()=>{biblioteca.ativo=selecionado;lista();marcar();};
  $('template-duplicar').onclick=()=>{if(biblioteca.modelos.length>=24)return estado('Limite de 24 versões.');const m=clonar(atual());m.id=crypto.randomUUID();m.nome=(m.nome.split(' / ')[0]+' · Minha versão').slice(0,120);biblioteca.modelos.push(m);selecionado=m.id;preencher();marcar();};
  $('template-zero').onclick=()=>{if(biblioteca.modelos.length>=24)return estado('Limite de 24 versões.');const m=clonar(catalogo[0]);m.id=crypto.randomUUID();m.design='livre';m.nome='Minha composição';m.descricao='Identidade criada do zero';m.cores={fundo:'#ffffff',papel:'#ffffff',tinta:'#243128',destaque:'#294332',secundaria:'#dce2dc'};biblioteca.modelos.push(m);selecionado=m.id;preencher();marcar();};
  $('template-excluir').onclick=()=>{if(selecionado===biblioteca.ativo)return estado('Defina outro modelo como padrão antes de excluir este.');biblioteca.modelos=biblioteca.modelos.filter(m=>m.id!==selecionado);selecionado=biblioteca.ativo;preencher();marcar();};
  $('template-desfazer').onclick=()=>{if(!historico.length)return;futuro.push(clonar(atual()));biblioteca.modelos[biblioteca.modelos.indexOf(atual())]=historico.pop();preencher();marcar();};
  $('template-refazer').onclick=()=>{if(!futuro.length)return;historico.push(clonar(atual()));biblioteca.modelos[biblioteca.modelos.indexOf(atual())]=futuro.pop();preencher();marcar();};
  $('template-logo').onchange=async e=>{const f=e.target.files[0];if(!f)return;if(f.size>500000||!['image/png','image/jpeg'].includes(f.type)){estado('Use um logotipo PNG ou JPEG de até 500 KB.');e.target.value='';return;}const id=selecionado;
    try {const valor=await new Promise((res,rej)=>{const r=new FileReader();r.onload=()=>res(r.result);r.onerror=rej;r.readAsDataURL(f);});if(id!==selecionado)return;guardar();atual().logo=valor;marcar();agendar();}catch{estado('Não foi possível ler o logotipo.');}e.target.value='';};
  $('template-fundo-imagem').onchange=async e=>{const f=e.target.files[0];if(!f)return;if(f.size>500000||!['image/png','image/jpeg'].includes(f.type)){estado('Use PNG ou JPEG de até 500 KB.');return;}const id=selecionado;try{const imagem=await new Promise((res,rej)=>{const r=new FileReader();r.onload=()=>res(r.result);r.onerror=rej;r.readAsDataURL(f);});if(id!==selecionado)return;guardar();atual().fundoImagem=imagem;atual().opacidade??=0.08;marcar();agendar();}catch{estado('Não foi possível ler a imagem de fundo.');}e.target.value='';};
  $('template-remover-fundo').onclick=()=>{guardar();atual().fundoImagem='';marcar();agendar();};
  $('template-remover-logo').onclick=()=>{guardar();atual().logo='';marcar();agendar();};
  $('template-atualizar').onclick=()=>{clearTimeout(timer);previa().catch(e=>estado(e.message));};
  function baixar(blob,nome){const u=URL.createObjectURL(blob),a=document.createElement('a');a.href=u;a.download=nome;a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);}
  $('template-baixar').onclick=async()=>{try{const r=await api('/previa',{modelo:atual(),empresa:$('perfil-nome').value.trim()||'Sua empresa',formato:'pdf'});baixar(await r.blob(),'amostra-pagina-assinaturas.pdf');}catch(e){estado(e.message);}};
  $('template-exportar').onclick=()=>baixar(new Blob([JSON.stringify(biblioteca,null,2)],{type:'application/json'}),'modelos-pagina-assinaturas.json');
  $('template-importar').onchange=async e=>{try{const f=e.target.files[0];if(!f)return;if(f.size>4000000)throw Error('A biblioteca deve ter até 4 MB.');const dados=JSON.parse(await f.text());const r=await api('/validar',dados);biblioteca=await r.json();selecionado=biblioteca.ativo;preencher();marcar();}catch(e){estado(e.message);}e.target.value='';};
  window.addEventListener('beforeunload',e=>{if(pendente){e.preventDefault();e.returnValue='';}});
  window.EditorTemplates={
    async carregar(valor){catalogo=await(await api('')).json();biblioteca=clonar(valor?.versao===2?valor:{versao:2,ativo:catalogo[0].id,modelos:catalogo});selecionado=biblioteca.ativo;pendente=false;$('editor-templates').hidden=false;preencher();if(!$('modelos-galeria').children.length)galeria().catch(e=>estado(e.message));},
    dados:()=>clonar(biblioteca),salvo(){pendente=false;estado('Modelo salvo. A página será usada nas próximas gerações do PDF.');}
  };
})();
