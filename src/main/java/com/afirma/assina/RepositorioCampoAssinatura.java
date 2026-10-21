package com.afirma.assina;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioCampoAssinatura extends JpaRepository<CampoAssinatura, UUID> {
	List<CampoAssinatura> findBySignatarioId(UUID signatarioId);
}
