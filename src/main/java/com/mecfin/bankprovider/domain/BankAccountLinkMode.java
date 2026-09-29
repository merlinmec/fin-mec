package com.mecfin.bankprovider.domain;

/** PENDING = usuário ainda não escolheu; LINKED = lançamentos entram na conta; IGNORED = não sincroniza. */
public enum BankAccountLinkMode {
    PENDING,
    LINKED,
    IGNORED
}
