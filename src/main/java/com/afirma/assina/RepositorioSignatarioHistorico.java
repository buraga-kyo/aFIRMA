package com.afirma.assina;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RepositorioSignatarioHistorico extends JpaRepository<SignatarioHistorico, UUID> {
    List<SignatarioHistorico> findBySignatarioIdOrderByCriadoEmAscIdAsc(UUID signatarioId);
}
