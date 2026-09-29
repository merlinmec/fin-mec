package com.mecfin.household.domain;

import java.util.UUID;

/**
 * O household de um usuário mudou (entrou por convite, saiu ou foi removido). A sessão guarda o
 * household no momento do login, então a identidade reage trocando o carimbo de segurança do
 * usuário: toda sessão dele cai na próxima requisição — o removido perde acesso na hora.
 */
public record HouseholdMembershipChangedEvent(UUID userId, Change change) {

    public enum Change {
        JOINED,
        LEFT,
        REMOVED
    }
}
