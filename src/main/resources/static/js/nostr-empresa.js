(() => {
  const porId=id=>document.getElementById(id);
  const base='/api/assineai/nostr';
  let autorizacao='', versao=0;
  const tokenAtual=()=> (porId('empresa-token')?.value||localStorage.getItem('assineai-token')||'').trim();
  const estado=texto=>{porId('firma-nostr-estado').textContent=texto;};
  async function pedir(caminho,dados,metodo='GET') {
    const token=tokenAtual();
    const r=await fetch(base+caminho,{method:metodo,headers:{Authorization:`Bearer ${token}`,...(dados?{'Content-Type':'application/json'}:{})},...(dados?{body:JSON.stringify(dados)}:{}),cache:'no-store'});
    if(!r.ok){const e=await r.json().catch(()=>({}));throw Error(e.mensagem||e.detail||`Operação recusada (${r.status}).`);}
    return r.status===204?null:r.json();
  }
  async function carregar() {
    const atual=++versao, acesso=tokenAtual();
    const vigente=()=>atual===versao&&acesso===tokenAtual();
    try {
      const config=await pedir('/estado');if(!vigente())return;autorizacao=config.autorizacao;
      porId('firma-nostr-autorizacao-texto').textContent=autorizacao;
      porId('firma-nostr-ativa').checked=config.ativa;
      porId('firma-nostr-nome').value=config.nome||porId('perfil-nome')?.value||'';
      porId('firma-nostr-sobre').value=config.sobre||'';
      porId('firma-nostr-chave').textContent=config.chavePublica?`Chave pública cadastrada: ${config.chavePublica}`:'Nenhuma identidade Nostr cadastrada.';
      const seletor=porId('firma-nostr-assinador');seletor.replaceChildren(new Option('Selecione um assinador autorizado para esta empresa',''));
      if(!config.servicoConfigurado){estado('Cadastre o assinador na tela de configurações do Inventor. Depois autorize aqui as publicações da empresa.');return;}
      const lista=await pedir('/identidades');if(!vigente())return;
      for(const item of lista.identidades||[])seletor.append(new Option(`${item.referencia} · ${item.chavePublica.slice(0,12)}…`,item.referencia));
      seletor.value=config.referencia||'';
      estado(config.ativa?'Publicações automáticas autorizadas. O perfil e os comprovantes são enviados pela fila.':'Publicações automáticas pausadas.');
    }catch(erro){if(vigente())estado(erro.message);}
  }
  porId('firma-nostr-atualizar').addEventListener('click',carregar);
  porId('firma-nostr-pausar').addEventListener('click',async()=>{
    try{await pedir('/pausar',{},'POST');porId('firma-nostr-ativa').checked=false;estado('Novas publicações foram pausadas. A anterioridade dos eventos já publicados continua sendo acompanhada. Esses eventos podem continuar disponíveis nos relays.');}
    catch(erro){estado(erro.message);}
  });
  porId('firma-nostr-form').addEventListener('submit',async evento=>{
    evento.preventDefault();
    const botao=porId('firma-nostr-salvar');botao.disabled=true;
    try{
      const ativa=porId('firma-nostr-ativa').checked;
      if(ativa&&!porId('firma-nostr-autorizar').checked)throw Error('Confirme a autorização para publicações públicas automáticas.');
      await pedir('/configuracao',{referencia:porId('firma-nostr-assinador').value,nome:porId('firma-nostr-nome').value.trim(),sobre:porId('firma-nostr-sobre').value.trim(),ativa,autorizacao:porId('firma-nostr-autorizar').checked?autorizacao:''},'PUT');
      porId('firma-nostr-autorizar').checked=false;await carregar();
    }catch(erro){estado(erro.message);}finally{botao.disabled=false;}
  });
  porId('empresa-token')?.addEventListener('change',carregar);
  document.addEventListener('firma:acesso-conectado',carregar);
  if((porId('empresa-token')?.value||localStorage.getItem('assineai-token')||'').trim())carregar();
})();
