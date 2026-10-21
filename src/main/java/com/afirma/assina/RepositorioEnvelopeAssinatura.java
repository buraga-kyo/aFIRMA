package com.afirma.assina;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioEnvelopeAssinatura extends JpaRepository<EnvelopeAssinatura, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select envelope from EnvelopeAssinatura envelope where envelope.id = :id")
	Optional<EnvelopeAssinatura> buscarParaFinalizar(@Param("id") UUID id);
}
