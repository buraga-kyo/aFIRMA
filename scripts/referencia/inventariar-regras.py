#!/usr/bin/env python3
"""Rastreabilidade das regras consultadas; armazena nomes e hashes, nunca o conteúdo dos arquivos."""
from pathlib import Path
import os,re,json,hashlib
origem=Path(os.environ.get('FIRMA_REFERENCIA_ROOT','/home/aluno/autentica/zapio'))
raiz=Path(__file__).resolve().parents[2]
fontes=[]
for pasta in ['model/src/consultas','model/src/services','model/src/scheduler','model/src/routes','model/src/API','model/src/middleware','model/src/ScriptsChatbot','model/src/AutenticaAI']:
 for arq in sorted((origem/pasta).rglob('*.js')):
  if arq.name.lower() in ['teste.js','test.js']:continue
  conteudo=arq.read_bytes();texto=conteudo.decode('utf-8',errors='replace')
  fontes.append({'arquivo':str(arq.relative_to(origem)),'sha256':hashlib.sha256(conteudo).hexdigest(),'funcoes':re.findall(r'exports\.([A-Za-z_][A-Za-z0-9_]*)\s*=',texto),'rotas':[{'metodo':m.upper(),'caminho':p} for m,p in re.findall(r'rotas\.(get|post|put|delete|patch)\("([^"]+)"',texto)],'excluido':arq.name=='TabelaPlano.js'})
saida=Path('/tmp/firma-referencias/regras.json');saida.parent.mkdir(parents=True,exist_ok=True);saida.write_text(json.dumps({'origem':str(origem),'criterio':'Rotas e rotinas operacionais. Planos excluídos. Helpers não chamados não são funcionalidades ativas.','fontes':fontes},ensure_ascii=False,indent=2)+'\n')
print(f'{len(fontes)} fontes registradas, sem copiar código ou credenciais.')
