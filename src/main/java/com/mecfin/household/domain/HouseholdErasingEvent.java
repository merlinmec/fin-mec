package com.mecfin.household.domain;

import java.util.UUID;

/**
 * Publicado (síncrono, na mesma transação) logo antes de apagar todos os dados de um household:
 * exclusão da conta do último membro ou troca do household pessoal por um compartilhado. Quem
 * guarda algo fora do banco (ex.: conexão no provedor de Open Finance) limpa aqui.
 */
public record HouseholdErasingEvent(UUID householdId) {
}
