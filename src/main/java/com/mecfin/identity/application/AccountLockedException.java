package com.mecfin.identity.application;

import com.mecfin.shared.exception.TooManyRequestsException;

// Mesma resposta (429) do rate limit por IP: para quem está do outro lado, as duas situações
// significam "espere e tente de novo".
public class AccountLockedException extends TooManyRequestsException {

    public AccountLockedException() {
        super("Muitas tentativas de login sem sucesso. Por segurança, a conta foi bloqueada por alguns minutos.");
    }
}
