package com.mecfin.bill.infra;

import com.mecfin.bill.domain.Bill;
import com.mecfin.bill.domain.BillStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    List<Bill> findAllByHouseholdIdOrderByDueDateAsc(UUID householdId);

    List<Bill> findAllByHouseholdIdAndStatusOrderByDueDateAsc(UUID householdId, BillStatus status);

    // Usado só pelo filtro "OVERDUE" (BillService.list) - status OVERDUE nunca é persistido,
    // então "está atrasada" é sempre OPEN + due_date no passado, calculado aqui na consulta.
    List<Bill> findAllByHouseholdIdAndStatusAndDueDateBeforeOrderByDueDateAsc(
            UUID householdId, BillStatus status, LocalDate date);

    // household_id faz parte do filtro (não só do "where exists"): garante que um id de
    // conta a pagar de outro household nunca é encontrado, mesmo que adivinhado (mitigação
    // IDOR/BOLA).
    Optional<Bill> findByIdAndHouseholdId(UUID id, UUID householdId);
}
