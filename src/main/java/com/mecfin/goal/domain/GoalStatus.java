package com.mecfin.goal.domain;

// Sempre derivado na leitura (ver GoalView), nunca persistido - mesmo padrão do OVERDUE de Bill.
public enum GoalStatus {
    ACTIVE,
    COMPLETED,
    OVERDUE,
    ARCHIVED
}
