package com.afirma.assina;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CofreCertificadoEmpresa {
    private final JdbcTemplate banco;
    private final ValidadorPedidoAssinatura validador;
    private final ServicoAssinaturaPdf assinador;
    private final boolean permitirVencido;
    private final byte[] chave;
    private final String identificadorChave;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public CofreCertificadoEmpresa(JdbcTemplate banco, ValidadorPedidoAssinatura validador, ServicoAssinaturaPdf assinador,
            @Value("${APP_CHAVE_COFRE_CERTIFICADOS:}") String chaveConfigurada,
            @Value("${APP_PERMITIR_CERTIFICADO_VENCIDO:false}") boolean permitirVencido) {
        this.banco = banco; this.validador = validador; this.assinador = assinador; this.permitirVencido = permitirVencido;
        try {
            chave = chaveConfigurada.isBlank() ? new byte[0] : Base64.getDecoder().decode(chaveConfigurada);
            if (chave.length != 0 && chave.length != 32) throw new IllegalArgumentException();
            identificadorChave = SegredosAssinatura.resumo(chave);
        } catch (IllegalArgumentException erro) {
            throw new IllegalArgumentException("APP_CHAVE_COFRE_CERTIFICADOS precisa conter 32 bytes em Base64");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Situacao salvar(long empresa, String responsavel, MultipartFile arquivo, String senhaInformada) {
        exigirChave();
        if (senhaInformada == null || senhaInformada.isBlank() || senhaInformada.length() > 256) throw new ErroAssinatura("Informe a senha do certificado (até 256 caracteres)");
        byte[] original = validador.validarCertificado(arquivo);
        char[] senhaOriginal = senhaInformada.toCharArray();
        byte[] senhaEmBytes = null;
        try {
            var titular = assinador.consultarCertificado(original, senhaOriginal, permitirVencido);
            byte[] protegido = cifrar(empresa, original, true);
            senhaEmBytes = senhaInformada.getBytes(StandardCharsets.UTF_8);
            byte[] senhaProtegida = cifrar(empresa, senhaEmBytes, true, "senha");
            banco.update("""
                INSERT INTO certificado_empresa(empresa_id,pfx_protegido,chave_protecao,titular,numero_serie,valido_ate,cadastrado_em,responsavel,senha_protegida,formato)
                VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,2) ON CONFLICT(empresa_id) DO UPDATE SET
                pfx_protegido=EXCLUDED.pfx_protegido,chave_protecao=EXCLUDED.chave_protecao,titular=EXCLUDED.titular,
                numero_serie=EXCLUDED.numero_serie,valido_ate=EXCLUDED.valido_ate,cadastrado_em=CURRENT_TIMESTAMP,responsavel=EXCLUDED.responsavel,senha_protegida=EXCLUDED.senha_protegida,formato=2
                """, empresa, protegido, identificadorChave, titular.titular(), titular.numeroSerie(), Timestamp.from(titular.validoAte()), responsavel, senhaProtegida);
            registrar(empresa, responsavel, "CADASTRADO", titular.numeroSerie());
            return situacao(empresa);
        } catch (java.security.cert.CertificateExpiredException erro) {
            throw new ErroAssinatura("Certificado vencido. Seu uso precisa estar permitido na configuração do servidor");
        } catch (java.security.cert.CertificateNotYetValidException erro) {
            throw new ErroAssinatura("Certificado ainda não é válido");
        } catch (ErroAssinatura | org.springframework.web.server.ResponseStatusException erro) {
            throw erro;
        } catch (Exception erro) {
            throw new ErroAssinatura("Não foi possível abrir o PFX/P12. Confira o arquivo e a senha");
        } finally {
            Arrays.fill(original, (byte) 0); Arrays.fill(senhaOriginal, '\0');
            if (senhaEmBytes != null) Arrays.fill(senhaEmBytes, (byte) 0);
        }
    }

    public Optional<CertificadoConfiguradoEmpresa.Material> carregar(long empresa) {
        var linhas = banco.queryForList("SELECT pfx_protegido,chave_protecao,senha_protegida,formato FROM certificado_empresa WHERE empresa_id=?", empresa);
        if (linhas.isEmpty()) return Optional.empty();
        exigirChave();
        if (!identificadorChave.equals(linhas.getFirst().get("chave_protecao"))) throw new ErroAssinatura("A chave do cofre mudou. Restaure a chave correspondente ou cadastre novamente o certificado");
        byte[] pfx = null; char[] senha = null;
        try {
            pfx = cifrar(empresa, (byte[]) linhas.getFirst().get("pfx_protegido"), false);
            if (((Number) linhas.getFirst().get("formato")).intValue() == 2) {
                byte[] senhaAberta = cifrar(empresa, (byte[]) linhas.getFirst().get("senha_protegida"), false, "senha");
                try {
                    var caracteres = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(senhaAberta));
                    senha = new char[caracteres.remaining()]; caracteres.get(senha);
                    if (caracteres.hasArray()) Arrays.fill(caracteres.array(), '\0');
                } finally { Arrays.fill(senhaAberta, (byte) 0); }
            } else senha = senhaInterna(empresa);
            var titular = assinador.consultarCertificado(pfx, senha, permitirVencido);
            return Optional.of(new CertificadoConfiguradoEmpresa.Material(pfx, senha, titular));
        } catch (Exception erro) {
            if (pfx != null) Arrays.fill(pfx, (byte) 0);
            if (senha != null) Arrays.fill(senha, '\0');
            throw new ErroAssinatura("Não foi possível utilizar o certificado cadastrado. Confira a validade e a configuração do cofre");
        }
    }

    public Situacao situacao(long empresa) {
        var linhas = banco.queryForList("SELECT titular,numero_serie,valido_ate,cadastrado_em FROM certificado_empresa WHERE empresa_id=?", empresa);
        if (linhas.isEmpty()) return new Situacao(false, "", "", null, null, false);
        var dados = linhas.getFirst();
        Instant validade = ((Timestamp) dados.get("valido_ate")).toInstant();
        return new Situacao(true, (String) dados.get("titular"), (String) dados.get("numero_serie"), validade,
                ((Timestamp) dados.get("cadastrado_em")).toInstant(), validade.isBefore(Instant.now()));
    }

    @Transactional
    public void remover(long empresa, String responsavel) {
        var atual = situacao(empresa);
        banco.update("DELETE FROM certificado_empresa WHERE empresa_id=?", empresa);
        banco.update("UPDATE \"Empresas\" SET \"EmpresaAssinaturaAutomatica\"=FALSE WHERE \"EmpresaId\"=?", empresa);
        if (atual.cadastrado()) registrar(empresa, responsavel, "REMOVIDO", atual.numeroSerie());
    }

    private void registrar(long empresa, String responsavel, String acao, String serie) {
        banco.update("INSERT INTO evento_certificado_empresa(id,empresa_id,acao,numero_serie,ocorrido_em,responsavel) VALUES(?,?,?,?,CURRENT_TIMESTAMP,?)",
                UUID.randomUUID(), empresa, acao, serie, responsavel);
    }
    private void exigirChave() { if (chave.length != 32) throw new ErroAssinatura("Configure a chave do cofre de certificados no servidor antes de cadastrar o PFX"); }
    private byte[] derivar(long empresa, String finalidade) throws Exception {
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(("afirma-cofre-1\n" + finalidade + "\n" + empresa).getBytes(StandardCharsets.UTF_8));
    }
    private char[] senhaInterna(long empresa) throws Exception {
        byte[] dados = derivar(empresa, "senha-pfx");
        try { return HexFormat.of().formatHex(dados).toCharArray(); } finally { Arrays.fill(dados, (byte) 0); }
    }
    private byte[] cifrar(long empresa, byte[] dados, boolean gravar) throws Exception {
        return cifrar(empresa, dados, gravar, "cifra");
    }
    private byte[] cifrar(long empresa, byte[] dados, boolean gravar, String finalidade) throws Exception {
        byte[] segredo = derivar(empresa, finalidade);
        try {
            byte[] nonce = new byte[12];
            if (gravar) ALEATORIO.nextBytes(nonce);
            else { if (dados.length < 28 || dados.length > 3145728) throw new ErroAssinatura("Certificado protegido inválido"); System.arraycopy(dados, 0, nonce, 0, 12); }
            var cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(gravar ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(segredo, "AES"), new GCMParameterSpec(128, nonce));
            cifra.updateAAD(("afirma-cofre-1:" + empresa + ":" + identificadorChave + (finalidade.equals("cifra") ? "" : ":" + finalidade)).getBytes(StandardCharsets.UTF_8));
            if (!gravar) return cifra.doFinal(dados, 12, dados.length - 12);
            byte[] protegido = cifra.doFinal(dados);
            return ByteBuffer.allocate(12 + protegido.length).put(nonce).put(protegido).array();
        } finally { Arrays.fill(segredo, (byte) 0); }
    }
    public record Situacao(boolean cadastrado, String titular, String numeroSerie, Instant validoAte, Instant cadastradoEm, boolean vencido) { }
}
