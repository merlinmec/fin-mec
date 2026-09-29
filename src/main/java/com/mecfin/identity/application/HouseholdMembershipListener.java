package com.mecfin.identity.application;

import com.mecfin.household.domain.HouseholdMembershipChangedEvent;
import com.mecfin.identity.domain.SecurityEventType;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.infra.SessionStampValidator;
import com.mecfin.identity.infra.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A sessão guarda o household do momento do login; quando ele muda, as sessões antigas não
 * podem continuar apontando para os dados do household anterior. Mesmo mecanismo da troca de
 * senha (Fase 14): carimbo de segurança novo = toda sessão do usuário cai na próxima requisição.
 */
@Component
public class HouseholdMembershipListener {

    private final UserRepository userRepository;
    private final SecurityEventService securityEvents;
    private final SessionStampValidator stampValidator;
    private final SessionService sessionService;

    public HouseholdMembershipListener(UserRepository userRepository, SecurityEventService securityEvents,
            SessionStampValidator stampValidator, SessionService sessionService) {
        this.userRepository = userRepository;
        this.securityEvents = securityEvents;
        this.stampValidator = stampValidator;
        this.sessionService = sessionService;
    }

    // Na mesma transação da mudança de household: ou as duas coisas acontecem, ou nenhuma.
    @EventListener
    public void rotateStamp(HouseholdMembershipChangedEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            user.rotateSecurityStamp();
            securityEvents.record(user.getId(), switch (event.change()) {
                case JOINED -> SecurityEventType.HOUSEHOLD_JOINED;
                case LEFT -> SecurityEventType.HOUSEHOLD_LEFT;
                case REMOVED -> SecurityEventType.HOUSEHOLD_REMOVED;
            }, ClientInfo.UNKNOWN);
        });
    }

    // Depois do commit: limpar o cache antes gravaria de novo o carimbo velho (ainda visível para
    // outras transações), e o removido manteria acesso pelo TTL do cache.
    @TransactionalEventListener
    public void afterCommit(HouseholdMembershipChangedEvent event) {
        stampValidator.evict(event.userId());
        // Removido por outra pessoa: apaga as sessões já (quem entrou ou saiu por conta própria
        // renova a sessão atual no controller e as outras caem pelo carimbo).
        if (event.change() == HouseholdMembershipChangedEvent.Change.REMOVED) {
            userRepository.findById(event.userId()).map(User::getEmail).ifPresent(sessionService::deleteAll);
        }
    }
}
