package com.mecfin.household.application;

import com.mecfin.household.domain.Household;
import com.mecfin.household.domain.HouseholdMember;
import com.mecfin.household.domain.HouseholdRole;
import com.mecfin.household.infra.HouseholdDataEraser;
import com.mecfin.household.infra.HouseholdMemberRepository;
import com.mecfin.household.infra.HouseholdRepository;
import com.mecfin.identity.domain.UserDeletingEvent;
import com.mecfin.identity.domain.UserRegisteredEvent;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final HouseholdDataEraser householdDataEraser;

    public HouseholdService(HouseholdRepository householdRepository, HouseholdMemberRepository householdMemberRepository,
            HouseholdDataEraser householdDataEraser) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.householdDataEraser = householdDataEraser;
    }

    /**
     * Tira o usuário do household quando a conta dele é excluída (Fase 14). Último membro
     * saindo = o household e todos os dados financeiros dele são apagados; com outros membros
     * (household compartilhado, ainda não exposto na UI), só a participação sai.
     */
    @EventListener
    @Transactional
    public void onUserDeleting(UserDeletingEvent event) {
        UUID userId = event.userId();
        householdMemberRepository.findHouseholdIdByUserId(userId).ifPresent(householdId -> {
            if (householdMemberRepository.countByHouseholdId(householdId) <= 1) {
                householdDataEraser.erase(householdId);
            } else {
                householdMemberRepository.deleteByUserId(userId);
            }
        });
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
