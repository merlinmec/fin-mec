package com.mecfin.dashboard.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryExpense(UUID categoryId, String categoryName, BigDecimal amount) {
}
