package com.mecfin.identity.infra;

import com.mecfin.identity.domain.RecoveryCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    List<RecoveryCode> findAllByUserIdAndUsedAtIsNull(UUID userId);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    @Modifying
    @Query("DELETE FROM RecoveryCode r WHERE r.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
