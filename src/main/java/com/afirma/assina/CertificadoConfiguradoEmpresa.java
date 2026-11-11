package com.afirma.assina;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CertificadoConfiguradoEmpresa {
    private final CofreCertificadoEmpresa cofre;
    private final String caminho;
    private final String senha;
    private final long empresaAutorizada;
    private final boolean permitirVencido;
    private final ServicoAssinaturaPdf assinador;

    public CertificadoConfiguradoEmpresa(@Value("${APP_ASSINEAI_CERTIFICADO_PATH:}") String caminho,
            @Value("${APP_ASSINEAI_CERTIFICADO_SENHA:${ASSINEAI_CERTIFICADO_SENHA:}}") String senha,
            @Value("${APP_ASSINEAI_CERTIFICADO_EMPRESA_ID:0}") long empresaAutorizada,
            @Value("${APP_PERMITIR_CERTIFICADO_VENCIDO:false}") boolean permitirVencido,
            ServicoAssinaturaPdf assinador, CofreCertificadoEmpresa cofre) {
        this.cofre = cofre;
        this.caminho = caminho; this.senha = senha; this.empresaAutorizada = empresaAutorizada;
        this.permitirVencido = permitirVencido; this.assinador = assinador;
    }

    public Material carregar(long empresa) {
        var cadastrado = cofre.carregar(empresa);
        if (cadastrado.isPresent()) return cadastrado.get();
        // Um certificado do servidor não pode ser usado por outra empresa da mesma instalação.
        if (empresa <= 0 || empresa != empresaAutorizada) throw new ErroAssinatura(
                "O certificado do servidor ainda não foi vinculado a esta empresa");
        if (caminho.isBlank() || senha.isBlank()) throw new ErroAssinatura("Configure o certificado e a senha da empresa antes de ativar a assinatura automática");
        byte[] dados = null;
        char[] segredo = senha.toCharArray();
        try (var entrada = Files.newInputStream(Path.of(caminho))) {
            dados = entrada.readNBytes(2 * 1024 * 1024 + 1);
            if (dados.length == 0 || dados.length > 2 * 1024 * 1024) throw new ErroAssinatura("O certificado deve ter até 2 MB");
            var titular = assinador.consultarCertificado(dados, segredo, permitirVencido);
            return new Material(dados, segredo, titular);
        } catch (Exception erro) {
            if (dados != null) Arrays.fill(dados, (byte) 0);
            Arrays.fill(segredo, '\0');
            throw new ErroAssinatura("Não foi possível usar o certificado configurado para esta empresa. Confira o arquivo, a senha e a validade");
        }
    }

    public void conferir(long empresa) { try (var material = carregar(empresa)) { } }
    public record Material(byte[] dados, char[] senha, ServicoAssinaturaPdf.DadosCertificado titular) implements AutoCloseable {
        @Override public void close() { Arrays.fill(dados, (byte) 0); Arrays.fill(senha, '\0'); }
    }
}
