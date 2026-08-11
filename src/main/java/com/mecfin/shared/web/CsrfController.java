package com.mecfin.shared.web;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Força a resolução do {@link CsrfToken} adiado (deferred loading do Spring Security),
 * fazendo o cookie XSRF-TOKEN ser efetivamente escrito na resposta. O frontend deve
 * chamar este endpoint ao carregar a aplicação e novamente após login/logout, já que
 * o token é renovado nesses momentos.
 */
@RestController
public class CsrfController {

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }
}
