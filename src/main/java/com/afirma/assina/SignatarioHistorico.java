package com.afirma.assina;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "signatario_historico")
public class SignatarioHistorico {
    @Id private UUID id;
    @Column(nullable = false) private UUID signatarioId;
    @Column(nullable = false, length = 120) private String status;
    @Column(nullable = false, length = 120) private String acao;
    @Column(length = 64) private String enderecoIp;
    @Column(columnDefinition = "text") private String dispositivo;
    @Column(nullable = false) private Instant criadoEm;
    protected SignatarioHistorico() {}
    public SignatarioHistorico(UUID signatarioId, String status, String acao, String ip, String dispositivo) {
        this.id = UUID.randomUUID(); this.signatarioId = signatarioId; this.status = status; this.acao = acao;
        this.enderecoIp = limitar(ip, 64); this.dispositivo = limitar(dispositivo, 1024); this.criadoEm = Instant.now();
    }
    private String limitar(String valor, int limite) { return valor == null ? null : valor.substring(0, Math.min(valor.length(), limite)); }
    public UUID getId() { return id; }
    public UUID getSignatarioId() { return signatarioId; }
    public String getStatus() { return status; }
    public String getAcao() { return acao; }
    public String getEnderecoIp() { return enderecoIp; }
    public String getDispositivo() { return dispositivo; }
    public Instant getCriadoEm() { return criadoEm; }
}
