package com.afirma.assina;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface RepositorioAssinaturaBlockchain extends JpaRepository<AssinaturaBlockchainPendente, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select assinatura from AssinaturaBlockchainPendente assinatura where assinatura.identificador = :identificador")
	Optional<AssinaturaBlockchainPendente> buscarParaConcluir(@Param("identificador") UUID identificador);

	long deleteByExpiraEmBefore(Instant instante);
}
