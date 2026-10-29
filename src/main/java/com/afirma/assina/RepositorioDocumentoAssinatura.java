package com.afirma.assina;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RepositorioDocumentoAssinatura extends JpaRepository<DocumentoAssinatura, UUID> {
    List<DocumentoAssinatura> findByEnvelopeIdOrderByPaginaInicio(UUID envelopeId);
}
