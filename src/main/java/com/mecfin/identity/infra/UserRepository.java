package com.mecfin.identity.infra;

import com.mecfin.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    // Consulta mínima (só o carimbo) feita a cada requisição autenticada pelo
    // SessionValidityFilter - ver SessionStampValidator.
    @Query("SELECT u.securityStamp FROM User u WHERE u.id = :id")
    Optional<UUID> findSecurityStampById(@Param("id") UUID id);
}
