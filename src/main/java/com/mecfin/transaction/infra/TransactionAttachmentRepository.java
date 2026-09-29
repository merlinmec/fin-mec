package com.mecfin.transaction.infra;

import com.mecfin.transaction.domain.TransactionAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionAttachmentRepository extends JpaRepository<TransactionAttachment, UUID> {

    List<TransactionAttachment> findAllByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

    long countByTransactionId(UUID transactionId);

    // transaction_id no filtro: o id do anexo sozinho nunca basta (IDOR entre lançamentos).
    Optional<TransactionAttachment> findByIdAndTransactionId(UUID id, UUID transactionId);
}
