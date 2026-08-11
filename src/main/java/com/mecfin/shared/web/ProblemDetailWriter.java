package com.mecfin.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Escreve respostas {@link ProblemDetail} fora do alcance do {@code DispatcherServlet}
 * (ex.: dentro da security filter chain, antes de qualquer controller ser invocado),
 * onde o {@code GlobalExceptionHandler} não consegue interceptar.
 */
@Component
public class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    public ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json;charset=UTF-8");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
