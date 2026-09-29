package com.mecfin.importing.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Forma canônica de uma descrição para comparar "é o mesmo estabelecimento": minúsculo, sem
 * acento, sem pontuação e sem tokens só numéricos. "COMPRA CARTÃO 4432 PADARIA SÃO JOÃO 12/09"
 * e "Compra cartao 9981 Padaria Sao Joao 03/10" viram a mesma coisa — os números mudam a cada
 * compra (final do cartão, data, NSU), o nome do lugar não.
 */
public final class DescriptionNormalizer {

    private DescriptionNormalizer() {
    }

    public static String normalize(String description) {
        if (description == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(description, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Arrays.stream(withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().split(" "))
                .filter(token -> !token.isEmpty() && !token.chars().allMatch(Character::isDigit))
                .collect(Collectors.joining(" "));
    }
}
