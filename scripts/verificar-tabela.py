#!/usr/bin/env python3
"""Testes de DOM e integração com Chrome headless, sem dependências adicionais."""
from pathlib import Path
from html import unescape
import json
import re
import shutil
import subprocess
import tempfile

raiz = Path(__file__).resolve().parents[1]
chrome = next((shutil.which(nome) for nome in ('google-chrome', 'chromium', 'chromium-browser') if shutil.which(nome)), None)
if not chrome:
    raise SystemExit('Instale Chrome/Chromium para executar os testes de DOM.')
recursos = raiz / 'src/main/resources/static'


def script(caminho):
    return '<script>' + caminho.read_text() + '</script>'


def css(caminho):
    # Não depender da rede de fontes durante testes de comportamento/layout.
    return '<style>' + re.sub(r'@import[^;]+;', '', caminho.read_text()) + '</style>'


nucleo = '<!doctype html><meta charset="utf-8">' + css(recursos / 'css/tabela-core.css') + '<body>'
nucleo += script(recursos / 'js/tabela-core.js')
nucleo += script(raiz / 'src/test/js/tabela-core.browser.js') + '</body>'

pagina = (raiz / 'src/main/resources/templates/documentos-assinatura.html').read_text()
pagina = re.sub(r'<script.*?</script>', '', pagina, flags=re.S)
pagina = re.sub(r'<link[^>]+href="(/css/[^\"]+)"[^>]*>', lambda m: css(recursos / m.group(1).lstrip('/')), pagina)
preparar = '''<script>
window.errosPagina = []; addEventListener('error', e => errosPagina.push(e.message));
window.chamadasAPI = [];
localStorage.clear();
window.fetch = async (url, options = {}) => {
  chamadasAPI.push({url, metodo: options.method || 'GET', body: options.body});
  if (options.method === 'PATCH') return new Response(null, {status: 204});
  return new Response(JSON.stringify([{id: 1, titulo: 'Contrato', data: '2026-10-29T12:00:00Z',
    responsavel: 'Firma', status: 'PENDENTE', quantidadeAssinaturas: 1, signatarios: [{nome: 'Ana'}], legado: true}]));
};
</script>'''
registrar = '''<script>
window.FirmaTabelaOriginal = window.FirmaTabela;
window.FirmaTabela = class extends FirmaTabelaOriginal {
  constructor(config) { super(config); window.tabelaTeste = this; }
};
</script>'''
pagina = pagina.replace('</body>', preparar + script(recursos / 'js/tabela-core.js') + registrar
                        + script(recursos / 'js/documentos-assinatura.js')
                        + script(raiz / 'src/test/js/documentos-tabela.browser.js') + '</body>')

falhas = 0
with tempfile.TemporaryDirectory(prefix='af-tabela-') as pasta:
    for nome, html, largura in [('nucleo', nucleo, 1440), ('trator-desktop', pagina, 1440), ('trator-estreito', pagina, 500)]:
        arquivo = Path(pasta) / (nome + '.html')
        arquivo.write_text(html)
        proc = subprocess.run([chrome, '--headless', '--no-sandbox', '--disable-gpu', '--allow-file-access-from-files',
                               f'--window-size={largura},1000', '--virtual-time-budget=5000', '--dump-dom', arquivo.as_uri()],
                              capture_output=True, text=True, timeout=30)
        match = re.search(r'<pre id="resultado-testes">(.*?)</pre>', proc.stdout, re.S)
        if not match:
            raise SystemExit(f'{nome}: testes não concluíram.\n' + proc.stderr[-2000:] + '\n' + proc.stdout[-2000:])
        resultados = json.loads(unescape(match.group(1)))
        for teste in resultados:
            print(('OK' if teste['ok'] else 'FALHOU') + f' [{nome}]: ' + teste['nome'])
            if not teste['ok']:
                falhas += 1
                print(teste['erro'])
raise SystemExit(1 if falhas else 0)
