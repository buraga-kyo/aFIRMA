package com.afirma.assina;
import java.time.Instant;
import jakarta.persistence.*;
@Entity
@Table(name = "desafio_assinatura")
public class DesafioAssinatura {
    @Id @Column(length = 64) private String id;
    @Column(length = 64) private String resumo;
    @Column(nullable = false) private java.util.UUID signatarioId;
    @Column(nullable = false, length = 20) private String canal;
    private Instant expiraEm;
    private Instant enviadoEm;
    private int tentativas;
    private boolean verificado;
    protected DesafioAssinatura() {}
    public DesafioAssinatura(java.util.UUID signatarioId, String canal, String resumo) { this.signatarioId = signatarioId; this.canal = canal; this.id = signatarioId + ":" + canal; this.resumo = resumo; enviadoEm = Instant.now(); expiraEm = enviadoEm.plusSeconds(600); }
    public Instant getEnviadoEm() { return enviadoEm; }
    public boolean verificar(String recebido) {
        if (tentativas >= 5 || Instant.now().isAfter(expiraEm) || verificado) return false;
        tentativas++;
        verificado = java.security.MessageDigest.isEqual(resumo.getBytes(java.nio.charset.StandardCharsets.UTF_8), recebido.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return verificado;
    }
    public boolean valido() { return verificado && Instant.now().isBefore(expiraEm); }
}
