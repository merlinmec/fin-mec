package com.mecfin.bill.domain;

// OVERDUE nunca é persistido (ver Bill/BillView) - só existe como valor calculado na leitura.
// A coluna status no banco só grava OPEN/PAID/CANCELED.
public enum BillStatus {
    OPEN,
    PAID,
    OVERDUE,
    CANCELED
}
