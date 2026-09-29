package com.mecfin.transaction.application;

// Alcance de uma edição/exclusão numa ocorrência de lançamento fixo (mesmo vocabulário do
// Organizze/Google Agenda). Em lançamento avulso só THIS faz sentido; ALL não existe de
// propósito - reescrever ocorrências já efetivadas mudaria saldo/relatório retroativamente.
public enum EditScope {
    THIS,
    THIS_AND_FUTURE
}
