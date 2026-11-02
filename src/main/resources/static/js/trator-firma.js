/** O trator de assinaturas define a base de todos os tratores daFIRMA. */
(() => {
 const recursos=Object.freeze({selecao:true,acoes:true,paginacao:true,selecionarColunas:true,filtrosHeader:true,ordenacao:true,redimensionamento:true,reordenacao:true,filtrosSalvos:true,buscaIA:true,edicao:true,detalhes:true,persistencia:true});
 function montar(config){
  const rodape=config.slots?.rodape;
  return new window.FirmaTabela({tamanhoPagina:10,tamanhosPagina:[5,10,50,100],larguraAcoes:176,...config,textos:{busca:'FALAR COM O BURRO DE CARGA...',pagina:({pagina,totalPaginas,tamanho})=>`ÁREA ${pagina} de ${totalPaginas} do TERRENO → ${tamanho} Sementes ▾`,...config.textos},recursos:{...recursos,...config.recursos},slots:{...config.slots,rodape:(destino,instancia)=>{
   if(typeof rodape==='function')rodape(destino,instancia);else if(rodape instanceof Node)destino.append(rodape);
   const selecao=document.createElement('span');selecao.className='trator-selecao';selecao.hidden=true;selecao.setAttribute('aria-live','polite');destino.insertBefore(selecao,destino.querySelector('.titulo-trator'));
  }},onRender:instancia=>{const selo=instancia.raiz.querySelector('.trator-selecao');if(selo){const n=instancia.obterSelecionados().length;selo.hidden=!n;selo.textContent=n+' selecionado'+(n===1?'':'s');}config.onRender?.(instancia);}});
 }
 function configurarLargura(main,botao,storageId){
  const modos=[['total','TELA INTEIRA'],['largo','80% DA TELA'],['centro','CENTRO']];
  const aplicar=modo=>{const escolhido=modos.find(m=>m[0]===modo)||modos[0];main.dataset.largura=escolhido[0];botao.textContent='▤ '+escolhido[1];try{localStorage.setItem(storageId,escolhido[0]);}catch{}};
  botao.onclick=()=>{const indice=modos.findIndex(m=>m[0]===main.dataset.largura);aplicar(modos[(indice+1)%modos.length][0]);};
  let salvo;try{salvo=localStorage.getItem(storageId);}catch{}aplicar(salvo||'total');
 }
 window.FirmaTrator=Object.freeze({recursos,montar,configurarLargura});
})();
