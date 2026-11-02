#!/usr/bin/env python3
"""Valida o chat real e sua navegação no Chrome, com API simulada e sem alterar dados."""
from pathlib import Path
import re,json,subprocess,tempfile,html
root=Path(__file__).resolve().parents[1]
static=root/'src/main/resources/static'
pagina=(root/'src/main/resources/templates/autentica-chat.html').read_text()
fragmento=(root/'src/main/resources/templates/fragments/sistema.html').read_text()
menus=''.join(f'<small class="firma-menu-grupo">{g}</small><a href="/sistema?modulo={m}"><span>◫</span><span>{g}</span></a>' for g,m in [('Operação','atendimentos'),('Cadastros','contatos'),('Comunicação','respostas')])
fragmento=re.sub(r'<th:block.*?</th:block>',menus,fragmento,flags=re.S)
pagina=pagina.replace('<div th:replace="~{fragments/sistema :: navegacao}"></div>',fragmento)
pagina=pagina.replace('<div th:replace="~{fragments/trator :: acesso(false)}"></div>',re.sub(r'<div class="origem-rodape".*?</div>','',(root/'src/main/resources/templates/fragments/trator.html').read_text(),flags=re.S))
js=re.findall(r'<script src="/js/([^"]+)"[^>]*></script>',pagina)
pagina=re.sub(r'<script.*?</script>','',pagina,flags=re.S)
pagina=re.sub(r'<link[^>]+href="/css/([^"]+)"[^>]*>',lambda m:'<style>'+re.sub(r'@import\s+url\([^)]*\)\s*;','',(static/'css'/m[1]).read_text())+'</style>',pagina)
mock='''<script>
window.contatoTeste={id:1,ContatoNome:'Marina',ContatoNumero:'5511999999999',ContatoEmail:'marina@example.test'};window.estadoTeste='pendente';window.chamadasDetalhadas=[];window.arquivosExportados=[];const criarUrl=URL.createObjectURL;URL.createObjectURL=b=>{arquivosExportados.push(b);return criarUrl(b);};const clicar=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){if(!this.download)return clicar.call(this);};
window.errosZapiou=[];window.chamadasZapiou=[];addEventListener('error',e=>errosZapiou.push(e.message));localStorage.setItem('assineai-token','teste');
window.fetch=async (url,options={})=>{chamadasZapiou.push(String(url));chamadasDetalhadas.push({url,metodo:options.method||'GET',dados:options.body?JSON.parse(options.body):null});const contato=window.contatoTeste;const a={id:1,ContatoId:1,ContatoNome:contato.ContatoNome,ContatoEmail:contato.ContatoEmail,CanalId:1,CanalNome:'Principal',AtendimentoStatus:window.estadoTeste,updatedAt:'2026-11-01T12:00:00Z',UltimaMensagem:'Olá, Marina',ContatoNumero:'5511999999999',ContatoAvatar:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a8f8AAAAASUVORK5CYII='};let d={itens:[],total:0};
if(url.includes('contexto')&&options.headers?.Authorization==='Bearer invalido')return new Response(JSON.stringify({mensagem:'Token inválido'}),{status:401});
if(url.includes('contexto'))d={nome:'Firma teste',clienteId:1,usuarioId:1,modulos:[{slug:'atendimentos'},{slug:'contatos',editavel:true},{slug:'respostas'},{slug:'canais',acoes:['importar']}]};
else if(url.endsWith('plano-busca'))d={steps:[{op:'column-filter',column:'status',type:'equals',value:'Pendente'}],summary:'Conversas pendentes'};
else if(url.endsWith('registros/contatos/1')&&options.method==='PUT'){Object.assign(contato,JSON.parse(options.body));d=contato;}
else if(url.endsWith('/historico'))d=[{id:1,AtendimentoProtocolo:'PROTO-1',AtendimentoStatus:window.estadoTeste}];
else if(url.endsWith('/acao')){window.estadoTeste=JSON.parse(options.body).acao==='finalizar'?'finalizado':'pendente';d={sucesso:true};}
else if(url.includes('registros/atendimentos')){const itens=[a,{...a,id:2,ContatoId:2,ContatoNome:'João',ContatoAvatar:null,AtendimentoStatus:'finalizado'},{...a,id:3,ContatoId:3,ContatoNome:'Carla',AtendimentoStatus:'emandamento'},{...a,id:4,ContatoId:4,ContatoNome:'Bia',AtendimentoStatus:'emandamento',AtendimentoBOTExterno:true}];d={itens,total:itens.length};}
else if(url.endsWith('conversas/1'))d={...a,contato,canal:{CanalNome:'Principal'},mensagens:[{id:1,MensagemTexto:'Histórico completo',createdAt:a.updatedAt}],acoes:[],tags:[]};
else if(url.includes('registros/contatos'))d={itens:[contato]};else if(url.includes('registros/canais'))d={itens:[{id:1,CanalNome:'Principal',CanalProvedorAPI:'Baileys'}]};
return new Response(JSON.stringify(d));};
</script>'''
pagina=pagina.replace('</body>',mock+''.join('<script>'+(static/'js'/f).read_text()+'</script>'+('<script>window.FirmaTabelaOriginal=window.FirmaTabela;window.FirmaTabela=class extends FirmaTabelaOriginal{constructor(c){super(c);window.tabelaZapiouTeste=this;}};</script>' if f=='tabela-core.js' else '') for f in js)+'<script>'+(root/'src/test/js/zapiou.browser.js').read_text()+'</script></body>')
falhas=0
with tempfile.TemporaryDirectory(prefix='zapiou-') as temp:
 p=Path(temp)/'chat.html';p.write_text(pagina)
 for largura in (1440,500):
  r=subprocess.run(['google-chrome','--headless','--no-sandbox','--disable-gpu','--allow-file-access-from-files',f'--window-size={largura},1000','--virtual-time-budget=5000',f'--screenshot=/tmp/zapiou-{largura}.png','--dump-dom',p.as_uri()],capture_output=True,text=True,timeout=30)
  m=re.search(r'<pre id="resultado-testes">(.*?)</pre>',r.stdout,re.S)
  if not m:raise SystemExit('Testes não concluíram: '+r.stdout[-1500:])
  for t in json.loads(html.unescape(m[1])):
   print(('OK' if t['ok'] else 'FALHOU')+f' [{largura}] '+t['nome'])
   if not t['ok']:falhas+=1;print(t['erro'])
raise SystemExit(bool(falhas))
