#!/usr/bin/env python3
"""Confere o pacote exportado sem transmitir os documentos para outros serviços."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tempfile
import zipfile


def exigir(condicao, mensagem):
    if not condicao:
        raise ValueError(mensagem)


def resumo(conteudo):
    return hashlib.sha256(conteudo).hexdigest()


def caminho(arquivo):
    extensoes = {"application/pdf": ".pdf", "application/json": ".json", "application/pkcs7-signature": ".p7s", "image/png": ".png", "image/jpeg": ".jpg", "text/plain": ".txt"}
    return "arquivos/" + arquivo["identificador"] + extensoes.get(arquivo["mime"], ".bin")


def verificar(nome_pacote, raizes=None, conferir_pdf=False):
    with zipfile.ZipFile(nome_pacote) as pacote:
        entradas = pacote.infolist()
        nomes = [entrada.filename for entrada in entradas]
        exigir(len(nomes) == len(set(nomes)), "O pacote possui entradas duplicadas.")
        exigir(len(nomes) <= 1000, "Quantidade de arquivos acima do limite.")
        for entrada in entradas:
            nome = PurePosixPath(entrada.filename)
            exigir(not nome.is_absolute() and ".." not in nome.parts and "\\" not in entrada.filename, "Caminho de arquivo inválido.")
            exigir(entrada.file_size <= 100 * 1024 * 1024, "Arquivo acima do limite de conferência.")
        exigir(sum(entrada.file_size for entrada in entradas) <= 2 * 1024 ** 3, "Pacote acima do limite de conferência.")
        manifesto = json.loads(pacote.read("manifesto.json"))
        exigir(manifesto.get("formato") == "afirma-pacote-1", "Formato de pacote desconhecido.")
        eventos = [json.loads(linha) for linha in pacote.read("eventos.jsonl").decode("utf-8").splitlines() if linha]
        anterior = "0" * 64
        registros = {}
        for sequencia, evento in enumerate(eventos, 1):
            dados = json.loads(evento["conteudo"])
            exigir(evento["sequencia"] == sequencia and dados["sequencia"] == sequencia, "Sequência de eventos incompleta.")
            exigir(dados["envelope"] == manifesto["envelope"] and dados["tipo"] == evento["tipo"]
                   and dados["participante"] == evento["participante"], "Metadados do evento divergentes.")
            exigir(evento["resumoAnterior"] == anterior, "Encadeamento de eventos divergente.")
            exigir(resumo((anterior + "\n" + evento["conteudo"]).encode("utf-8")) == evento["resumoEvento"], "Conteúdo de evento alterado.")
            anterior = evento["resumoEvento"]
            if evento["tipo"] == "ARQUIVO_PRESERVADO":
                registro = dados["informacoes"]
                exigir(registro["arquivo"] not in registros, "Registro de arquivo duplicado.")
                registros[registro["arquivo"]] = registro
        exigir(len(eventos) == manifesto["conferencia"]["quantidadeEventos"] and anterior == manifesto["conferencia"]["resumoUltimoEvento"], "Histórico divergente do manifesto.")
        arquivos = manifesto["arquivos"]
        exigir(len(arquivos) == len(registros), "Arquivos ausentes ou acrescentados sem registro.")
        por_identificador = {}
        por_tipo = {}
        for arquivo in arquivos:
            registro = registros.get(arquivo["identificador"])
            exigir(registro is not None, "Arquivo sem evento de preservação.")
            for atributo in ["tipo", "nome", "mime", "tamanho", "resumoSha256"]:
                exigir(arquivo[atributo] == registro[atributo], "Metadados do arquivo divergentes.")
            conteudo = pacote.read(caminho(arquivo))
            exigir(len(conteudo) == arquivo["tamanho"] and resumo(conteudo) == arquivo["resumoSha256"], "Arquivo adulterado: " + arquivo["identificador"])
            por_identificador[arquivo["identificador"]] = arquivo
            por_tipo.setdefault(arquivo["tipo"], []).append(arquivo)
        exigir(len(por_tipo.get("MANIFESTO_ASSINADO", [])) == 1 and len(por_tipo.get("SELO_MANIFESTO", [])) == 1,
               "O pacote ainda não possui um único manifesto com assinatura criptográfica.")
        arquivo_manifesto = por_tipo["MANIFESTO_ASSINADO"][0]
        arquivo_selo = por_tipo["SELO_MANIFESTO"][0]
        conteudo_manifesto = pacote.read(caminho(arquivo_manifesto))
        manifesto_assinado = json.loads(conteudo_manifesto)
        limite = manifesto_assinado["quantidadeEventos"]
        exigir(manifesto_assinado["envelope"] == manifesto["envelope"] and 0 < limite <= len(eventos)
               and eventos[limite - 1]["resumoEvento"] == manifesto_assinado["resumoUltimoEvento"], "O histórico não corresponde ao manifesto assinado.")
        for arquivo in manifesto_assinado["arquivos"]:
            exigir(arquivo == por_identificador.get(arquivo["identificador"]), "Versão aceita diverge do manifesto assinado.")
        exigir(shutil.which("openssl"), "Instale OpenSSL para conferir a assinatura do manifesto.")
        with tempfile.TemporaryDirectory(prefix="afirma-evidencias-") as temporario:
            pasta = Path(temporario)
            (pasta / "manifesto.json").write_bytes(conteudo_manifesto)
            (pasta / "manifesto.p7s").write_bytes(pacote.read(caminho(arquivo_selo)))
            comando = ["openssl", "cms", "-verify", "-binary", "-inform", "DER", "-in", str(pasta / "manifesto.p7s"),
                       "-content", str(pasta / "manifesto.json"), "-out", os.devnull]
            comando += ["-CAfile", str(raizes), "-purpose", "any", "-cades"] if raizes else ["-noverify"]
            retorno = subprocess.run(comando, capture_output=True, timeout=20)
            exigir(retorno.returncode == 0, "A assinatura do manifesto não pôde ser confirmada: " + retorno.stderr.decode("utf-8", errors="replace"))
            if conferir_pdf:
                exigir(shutil.which("pdfsig") and shutil.which("pdftotext"), "Instale Poppler (pdfsig e pdftotext) para conferir o PDF.")
                exigir(len(por_tipo.get("PDF_FINAL", [])) == 1, "PDF final ausente ou ambíguo.")
                pdf = pasta / "documento-final.pdf"
                pdf.write_bytes(pacote.read(caminho(por_tipo["PDF_FINAL"][0])))
                ambiente = {**os.environ, "LC_ALL": "C"}
                resultado = subprocess.run(["pdfsig", "-nocert", str(pdf)], capture_output=True, text=True, env=ambiente, timeout=30)
                exigir(resultado.returncode == 0 and "Signature Validation: Signature is Valid." in resultado.stdout
                       and "Total document signed" in resultado.stdout, "A assinatura que cobre o PDF final não foi confirmada pelo Poppler.")
                texto = subprocess.run(["pdftotext", str(pdf), "-"], capture_output=True, text=True, env=ambiente, timeout=30)
                exigir(texto.returncode == 0 and resumo(conteudo_manifesto) in texto.stdout
                       and arquivo_selo["resumoSha256"] in texto.stdout, "O PDF não contém o vínculo esperado com o manifesto e seu selo.")
    print("Arquivos, histórico e assinatura criptográfica do manifesto conferidos.")
    print("Confiança da cadeia conferida com o arquivo fornecido." if raizes else "Cadeia de confiança não conferida. Use --raizes com autoridades obtidas de fonte confiável.")
    if conferir_pdf:
        print("Assinatura do PDF final e vínculo com o manifesto conferidos pelo Poppler.")
    print("Esta conferência não atesta identidade civil, poderes de representação ou validade jurídica do ato.")


if __name__ == "__main__":
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("pacote", type=Path)
    argumentos.add_argument("--raizes", type=Path)
    argumentos.add_argument("--verificar-pdf", action="store_true")
    opcoes = argumentos.parse_args()
    try:
        verificar(opcoes.pacote, opcoes.raizes, opcoes.verificar_pdf)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, subprocess.SubprocessError) as erro:
        argumentos.exit(1, "Conferência recusada: " + str(erro) + "\n")
