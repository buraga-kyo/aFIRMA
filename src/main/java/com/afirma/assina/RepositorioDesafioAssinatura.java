package com.afirma.assina;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface RepositorioDesafioAssinatura extends JpaRepository<DesafioAssinatura, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DesafioAssinatura d where d.id = :id")
    Optional<DesafioAssinatura> bloquear(@Param("id") String id);
}
