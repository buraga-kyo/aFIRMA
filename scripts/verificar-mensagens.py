#!/usr/bin/env python3
"""Verifica os avisos compartilhados em desktop e celular, sem rede ou dados reais."""
from pathlib import Path
import subprocess,tempfile,re,json,html
raiz=Path(__file__).resolve().parents[1]
static=raiz/'src/main/resources/static'
css=(static/'css/mensagens-firma.css').read_text()
js=(static/'js/mensagens-firma.js').read_text()
testes=(raiz/'src/test/js/mensagens-firma.browser.js').read_text()
pagina='<!doctype html><meta charset="utf-8"><style>body{margin:0;background:#0E2117;color:#F2F7EC}'+css+'</style><body><script>window.errosMensagens=[];addEventListener("error",e=>errosMensagens.push(e.message));</script><script>'+js+'</script><script>'+testes+'</script></body>'
falhas=0
with tempfile.TemporaryDirectory(prefix='firma-mensagens-') as temp:
 p=Path(temp)/'mensagens.html';p.write_text(pagina)
 for largura in (1440,500):
  r=subprocess.run(['google-chrome','--headless','--no-sandbox','--disable-gpu',f'--window-size={largura},1000','--virtual-time-budget=3000','--dump-dom',p.as_uri()],capture_output=True,text=True,timeout=30)
  m=re.search('<pre id="resultado-testes">(.*?)</pre>',r.stdout,re.S)
  if not m:raise SystemExit('Testes não concluíram: '+r.stdout[-1200:]+r.stderr[-1200:])
  for t in json.loads(html.unescape(m[1])):
   print(('OK' if t['ok'] else 'FALHOU')+f' [{largura}] '+t['nome'])
   if not t['ok']:falhas+=1;print(t['erro'])
raise SystemExit(bool(falhas))
