package com.mecfin.identity.infra;

import com.mecfin.identity.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    // Contas de visitante da demonstração (ver VisitorAccountService): identificadas pelo domínio
    // reservado do e-mail, sem coluna própria no schema.
    long countByEmailEndingWith(String suffix);

    @Query("SELECT u.id FROM User u WHERE u.email LIKE CONCAT('%', :suffix) AND u.createdAt < :before")
    List<UUID> findIdsByEmailEndingWithAndCreatedAtBefore(@Param("suffix") String suffix,
            @Param("before") Instant before);

    // Consulta mínima (só o carimbo) feita a cada requisição autenticada pelo
    // SessionValidityFilter - ver SessionStampValidator.
    @Query("SELECT u.securityStamp FROM User u WHERE u.id = :id")
    Optional<UUID> findSecurityStampById(@Param("id") UUID id);
}
