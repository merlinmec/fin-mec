package com.mecfin.household.application;

import com.mecfin.household.domain.Household;
import com.mecfin.household.domain.HouseholdMember;
import com.mecfin.household.domain.HouseholdRole;
import com.mecfin.household.infra.HouseholdMemberRepository;
import com.mecfin.household.infra.HouseholdRepository;
import com.mecfin.identity.domain.UserRegisteredEvent;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;

    public HouseholdService(HouseholdRepository householdRepository, HouseholdMemberRepository householdMemberRepository) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
    }

    /**
     * Reage ao registro de um novo usuário (Fase 1) criando o household pessoal
     * dele. O listener padrão do Spring é síncrono: como {@code AuthService.register()}
     * publica o evento de dentro da sua própria transação, esta criação entra na
     * MESMA transação — se falhar, o registro inteiro é revertido, então nunca
     * existe um usuário sem household.
     */
    @EventListener
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        createForNewUser(event.userId(), event.email());
    }

    Household createForNewUser(UUID userId, String ownerEmail) {
        Household household = householdRepository.save(new Household("Financeiro de " + ownerEmail));
        householdMemberRepository.save(new HouseholdMember(household.getId(), userId, HouseholdRole.OWNER));
        return household;
    }
}
