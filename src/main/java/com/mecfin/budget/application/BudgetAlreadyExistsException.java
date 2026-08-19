package com.mecfin.budget.application;

import com.mecfin.shared.exception.ConflictException;
import java.time.YearMonth;
import java.util.UUID;

public class BudgetAlreadyExistsException extends ConflictException {

    public BudgetAlreadyExistsException(UUID categoryId, YearMonth referenceMonth) {
        super("Já existe orçamento para a categoria " + categoryId + " em " + referenceMonth);
    }
}
