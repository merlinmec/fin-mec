package com.mecfin.importing.domain;

public enum ImportFormat {
    OFX,
    CSV,
    // Lançamentos vindos da sincronização com o banco (Open Finance, Fase 16).
    BANK_SYNC
}
