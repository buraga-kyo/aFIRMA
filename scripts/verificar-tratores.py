#!/usr/bin/env python3
"""Confere o padrão visual e a busca paginada de todos os cadastros, sem serviços externos."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import html, json, re, subprocess, tempfile

raiz = Path(__file__).resolve().parents[1]
recursos = raiz / 'src/main/resources/static'
modulos = json.loads((raiz / 'src/main/resources/relacionamento/modulos.json').read_text())
for modulo in modulos:
    modulo.update(campos=[], criavel=True, acoes=[])
contexto = dict(clienteId=1, usuarioId=1, nome='Empresa teste', modulos=modulos)
pagina = (raiz / 'src/main/resources/templates/relacionamento.html').read_text()
fragmento = (raiz / 'src/main/resources/templates/fragments/sistema.html').read_text()
menus = ''.join(f'<small class="firma-menu-grupo">{m["grupo"]}</small><a href="/sistema?modulo={m["slug"]}"><span>◫</span><span>{m["titulo"]}</span></a>' for m in modulos)
fragmento = re.sub(r'<th:block.*?</th:block>', menus, fragmento, flags=re.S)
pagina = pagina.replace('<div th:replace="~{fragments/sistema :: navegacao}"></div>', fragmento)
pagina = pagina.replace('<div th:replace="~{fragments/trator :: acesso(false)}"></div>', re.sub(r'<div class="origem-rodape".*?</div>', '', (raiz / 'src/main/resources/templates/fragments/trator.html').read_text(), flags=re.S))
arquivos_js = re.findall(r'<script src="/js/([^"]+)"[^>]*></script>', pagina)
pagina = re.sub(r'<script.*?</script>', '', pagina, flags=re.S)
pagina = re.sub(r'<link[^>]+href="/css/([^"]+)"[^>]*>', lambda m: '<style>' + re.sub(r'@import\s+url\([^)]*\)\s*;', '', (recursos / 'css' / m[1]).read_text()) + '</style>', pagina)
mock = '<script>window.contextoTeste=' + json.dumps(contexto) + ";window.errosTeste=[];window.consultasTeste=[];addEventListener('error',e=>errosTeste.push(e.message));window.fetch=async(url,opcoes={})=>{consultasTeste.push(String(url));return new Response(JSON.stringify(String(url).endsWith('/contexto')?contextoTeste:{itens:[],total:0}));};</script>"
scripts = ''.join('<script>' + (recursos / 'js' / nome).read_text() + '</script>' for nome in arquivos_js)
testes = r"""<script>
(async()=>{
const esperar=()=>new Promise(r=>setTimeout(r,40));
const conferir=(valor,mensagem)=>{if(!valor)throw Error(mensagem)};
let resultado;
try{
 await esperar();
 conferir(!errosTeste.length,errosTeste.join('; '));
 conferir(!consultasTeste.some(u=>u.includes('/registros/')),'Dados carregados antes da conexão');
 document.getElementById('lista-token').value='teste';
 document.getElementById('lista-conectar').click();await esperar();await esperar();
 const raiz=document.getElementById('trator-montagem'),filtro=raiz.querySelector('.trator-burro-fixo'),faixa=raiz.querySelector('.trator-faixa-abas');
 conferir(raiz.classList.contains('trator-compartilhado'),'Tabela fora da base compartilhada');
 conferir(raiz.getBoundingClientRect().right<=innerWidth,'Trator ultrapassa a largura da tela');
 conferir(filtro&&getComputedStyle(filtro).position==='absolute','Burro de Carga não está à frente das abas');
 conferir(getComputedStyle(filtro.querySelector('form')).minHeight==='40px','Filtro fora da altura comum');
 conferir(raiz.querySelector('.trator-marca').getBoundingClientRect().height===50,'Aba da marca fora do padrão');
 conferir(faixa.lastElementChild.classList.contains('trator-abas-finais'),'Telas não é a última aba');
 conferir(faixa.lastElementChild.textContent==='TELAS','Mais de uma aba de telas');
 conferir(!faixa.querySelector('select')&&!faixa.querySelector('input'),'Controles de consulta entre as abas');
 conferir(!document.getElementById('trator-cadastro')&&!raiz.querySelector('.trator-lista-cabecalho'),'Cabeçalho extra ou seletor Cadastro presente');
 conferir(raiz.querySelector('.lista-rodape #relacionamento-atualizar'),'Atualizar fora do rodapé');
 conferir(raiz.querySelector('.lista-toolbar').nextElementSibling.classList.contains('tabela-painel'),'Faixa extra entre abas e tabela');
 conferir(raiz.querySelector('#alternar-largura'),'Controle de largura ausente');
 conferir(getComputedStyle(raiz.querySelector('[id$="colunas-botao"]')).getPropertyValue('--cor-aba').trim()==='#d2bc8e','Colunas sem papel de roça');
 const entrada=filtro.querySelector('input');entrada.value='nome de teste';filtro.querySelector('form').requestSubmit();await esperar();
 conferir(consultasTeste.some(u=>u.includes('busca=nome+de+teste')),'Busca não chegou ao servidor');
 const tabelaOriginal=raiz.querySelector('table');
 document.dispatchEvent(new CustomEvent('firma:contexto',{detail:contextoTeste}));await esperar();
 conferir(raiz.querySelectorAll('.lista-toolbar').length===1&&raiz.querySelectorAll('.trator-abas-finais').length===1,'Reconexão duplicou controles');
 conferir(raiz.querySelector('table')!==tabelaOriginal,'Instância não foi remontada');
 conferir(!errosTeste.length,errosTeste.join('; '));
 resultado={ok:true};
}catch(erro){resultado={ok:false,erro:erro.stack,avisos:document.getElementById('relacionamento-aviso')?.textContent,erros:errosTeste}}
const saida=document.createElement('pre');saida.id='resultado-trator';saida.textContent=JSON.stringify(resultado);document.body.append(saida);
})();</script>"""

def verificar(tarefa):
    modulo, largura, pasta = tarefa
    arquivo = Path(pasta) / f'{modulo["slug"]}-{largura}.html'
    conteudo = pagina.replace('<body ', f'<body data-modulo="{modulo["slug"]}" ')
    arquivo.write_text(conteudo.replace('</body>', mock + scripts + testes + '</body>'))
    comando = ['google-chrome', '--headless', '--no-sandbox', '--disable-gpu', '--allow-file-access-from-files', f'--window-size={largura},1000', '--virtual-time-budget=4000', '--dump-dom', arquivo.as_uri()]
    if modulo['slug']=='canais':
        comando.insert(-1,f'--screenshot=/tmp/firma-cadastros-{largura}.png')
    processo = subprocess.run(comando, capture_output=True, text=True, timeout=40)
    trecho = re.search(r'<pre id="resultado-trator">(.*?)</pre>', processo.stdout, re.S)
    resultado = json.loads(html.unescape(trecho[1])) if trecho else dict(ok=False, erro=processo.stderr[-500:])
    return modulo['slug'], largura, resultado

with tempfile.TemporaryDirectory(prefix='firma-tratores-') as pasta:
    tarefas = [(m, largura, pasta) for m in modulos for largura in (1440,500)]
    with ThreadPoolExecutor(max_workers=3) as executor:
        resultados = list(executor.map(verificar, tarefas))
for slug, largura, resultado in resultados:
    print(('OK' if resultado['ok'] else 'FALHOU') + f' [{largura}] {slug}')
    if not resultado['ok']: print(json.dumps(resultado, ensure_ascii=False))
raise SystemExit(any(not resultado['ok'] for _,_,resultado in resultados))
