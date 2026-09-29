package com.mecfin.household.application;

import com.mecfin.household.domain.Household;
import com.mecfin.household.domain.HouseholdErasingEvent;
import com.mecfin.household.domain.HouseholdMember;
import com.mecfin.household.domain.HouseholdRole;
import com.mecfin.household.infra.HouseholdDataEraser;
import com.mecfin.household.infra.HouseholdMemberRepository;
import com.mecfin.household.infra.HouseholdRepository;
import com.mecfin.identity.domain.UserDeletingEvent;
import com.mecfin.identity.domain.UserRegisteredEvent;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida do household: nasce com o usuário, morre com o último membro. */
@Service
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final HouseholdDataEraser householdDataEraser;
    private final ApplicationEventPublisher events;

    public HouseholdService(HouseholdRepository householdRepository, HouseholdMemberRepository householdMemberRepository,
            HouseholdDataEraser householdDataEraser, ApplicationEventPublisher events) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.householdDataEraser = householdDataEraser;
        this.events = events;
    }

    /**
     * Tira o usuário do household quando a conta dele é excluída (Fase 14). Último membro
     * saindo = o household e todos os dados financeiros dele são apagados. Com outros membros,
     * só a participação sai — e se quem sai é o dono, a posse passa para o membro mais antigo
     * (Fase 17), para o household nunca ficar sem ninguém que possa convidar ou remover.
     */
    @EventListener
    @Transactional
    public void onUserDeleting(UserDeletingEvent event) {
        UUID userId = event.userId();
        householdMemberRepository.findByUserId(userId).ifPresent(membership -> {
            UUID householdId = membership.getHouseholdId();
            if (householdMemberRepository.countByHouseholdId(householdId) <= 1) {
                erase(householdId);
                return;
            }
            if (membership.getRole() == HouseholdRole.OWNER) {
                householdMemberRepository.findAllByHouseholdIdOrderByJoinedAtAsc(householdId).stream()
                        .filter(m -> !m.getUserId().equals(userId))
                        .findFirst()
                        .ifPresent(heir -> heir.changeRole(HouseholdRole.OWNER));
            }
            householdMemberRepository.deleteByUserId(userId);
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

    /** Apaga o household inteiro, avisando antes quem guarda algo fora do banco. */
    void erase(UUID householdId) {
        events.publishEvent(new HouseholdErasingEvent(householdId));
        householdDataEraser.erase(householdId);
    }
}
