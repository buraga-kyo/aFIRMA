package com.afirma.assina;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioEnvelopeAssinatura extends JpaRepository<EnvelopeAssinatura, UUID> {
	interface ResumoEnvelope {
		UUID getId();
		String getNomeDocumento();
        String getNomeEmpresa();
        Boolean getEmpresaParticipante();
        java.time.Instant getEmpresaAssinouEm();
		java.time.Instant getCriadoEm();
		String getCriador();
        String getStatus();
		java.time.Instant getFinalizadoEm();
	}

    @Query(value = """
        SELECT id, nome_documento AS "nomeDocumento", nome_empresa AS "nomeEmpresa", criado_em AS "criadoEm",
               criador, status, finalizado_em AS "finalizadoEm", empresa_assinou_em AS "empresaAssinouEm",
               COALESCE(CAST(CAST(configuracao_registro_assinatura AS jsonb)->>'empresaParticipante' AS boolean), TRUE) AS "empresaParticipante"
        FROM envelope_assinatura WHERE criador=:criador AND excluido_em IS NULL ORDER BY criado_em DESC
        """, nativeQuery = true)
    List<ResumoEnvelope> findResumoByCriadorAndExcluidoEmIsNullOrderByCriadoEmDesc(@Param("criador") String criador);
	
	List<EnvelopeAssinatura> findByCriadorAndExcluidoEmIsNullOrderByCriadoEmDesc(String criador);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select envelope from EnvelopeAssinatura envelope where envelope.id = :id")
	Optional<EnvelopeAssinatura> buscarParaFinalizar(@Param("id") UUID id);
}
