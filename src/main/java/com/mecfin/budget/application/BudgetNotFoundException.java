package com.mecfin.budget.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class BudgetNotFoundException extends NotFoundException {

    public BudgetNotFoundException(UUID id) {
        super("Orçamento não encontrado: " + id);
    }
}
