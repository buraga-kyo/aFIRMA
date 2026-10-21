package com.afirma.assina;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioSignatarioEnvelope extends JpaRepository<SignatarioEnvelope, UUID> {
	List<SignatarioEnvelope> findByEnvelopeIdOrderByNome(UUID envelopeId);
	Optional<SignatarioEnvelope> findByResumoConvite(String resumoConvite);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select signatario from SignatarioEnvelope signatario where signatario.resumoConvite = :resumo")
	Optional<SignatarioEnvelope> buscarConviteParaAssinar(@Param("resumo") String resumo);
}
