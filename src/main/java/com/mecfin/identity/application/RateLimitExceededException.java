package com.mecfin.identity.application;

import com.mecfin.shared.exception.TooManyRequestsException;

public class RateLimitExceededException extends TooManyRequestsException {

    public RateLimitExceededException() {
        super("Muitas tentativas. Tente novamente mais tarde.");
    }
}
