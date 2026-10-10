package com.mecfin.demo.application;

import com.mecfin.shared.exception.TooManyRequestsException;

/** Teto de contas de visitante ativas ao mesmo tempo: protege o banco gratuito da demonstração. */
public class DemoFullException extends TooManyRequestsException {

    public DemoFullException() {
        super("A demonstração está com muitos visitantes agora. Tente de novo em alguns minutos.");
    }
}
