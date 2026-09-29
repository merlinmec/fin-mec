package com.mecfin.household.application;

import com.mecfin.household.domain.Household;
import com.mecfin.household.domain.HouseholdInvite;
import com.mecfin.household.domain.HouseholdInviteCreatedEvent;
import com.mecfin.household.domain.HouseholdMember;
import com.mecfin.household.domain.HouseholdMembershipChangedEvent;
import com.mecfin.household.domain.HouseholdMembershipChangedEvent.Change;
import com.mecfin.household.domain.HouseholdRole;
import com.mecfin.household.infra.HouseholdDataEraser;
import com.mecfin.household.infra.HouseholdInviteRepository;
import com.mecfin.household.infra.HouseholdMemberRepository;
import com.mecfin.household.infra.HouseholdRepository;
import com.mecfin.shared.exception.ConflictException;
import com.mecfin.shared.exception.ForbiddenException;
import com.mecfin.shared.exception.NotFoundException;
import com.mecfin.shared.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Household compartilhado (Fase 17). Regras que importam:
 * <ul>
 *   <li>só o dono (OWNER) convida, remove, renomeia e transfere a posse;</li>
 *   <li>o convite é de uso único, vale 7 dias, guarda só o hash do token e só pode ser aceito
 *       pela conta do e-mail convidado;</li>
 *   <li>cada usuário está em um household por vez: aceitar troca o household pessoal pelo
 *       compartilhado, e o pessoal é apagado — com dados dentro, só com confirmação explícita
 *       (não existe "mesclar" dois households: categorias, contas e saldos colidiriam);</li>
 *   <li>quem sai ou é removido ganha um household pessoal novo e vazio, e todas as sessões dele
 *       caem na hora (carimbo de segurança, ver {@link HouseholdMembershipChangedEvent}).</li>
 * </ul>
 */
@Service
public class HouseholdSharingService {

    public static final int MAX_MEMBERS = 6;
    static final Duration INVITE_VALIDITY = Duration.ofDays(7);

    public record Member(UUID userId, String email, HouseholdRole role, Instant joinedAt, boolean you) {
    }

    public record Invite(UUID id, String email, Instant createdAt, Instant expiresAt) {
    }

    /** Convites pendentes só aparecem para o dono. */
    public record Overview(UUID id, String name, HouseholdRole myRole, List<Member> members, List<Invite> invites) {
    }

    /** O link sai só nesta resposta (o banco guarda o hash): o dono pode mandar por onde quiser. */
    public record InviteCreated(Invite invite, String acceptUrl) {
    }

    public record InvitePreview(String householdName, String invitedByEmail, int memberCount, boolean emailMatches,
            boolean alreadyMember, boolean mustLeaveCurrent, boolean hasPersonalData) {
    }

    private final HouseholdRepository households;
    private final HouseholdMemberRepository members;
    private final HouseholdInviteRepository invites;
    private final HouseholdService lifecycle;
    private final HouseholdDataEraser eraser;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public HouseholdSharingService(HouseholdRepository households, HouseholdMemberRepository members,
            HouseholdInviteRepository invites, HouseholdService lifecycle, HouseholdDataEraser eraser,
            ApplicationEventPublisher events, Clock clock, @Value("${mecfin.app.base-url}") String baseUrl) {
        this.households = households;
        this.members = members;
        this.invites = invites;
        this.lifecycle = lifecycle;
        this.eraser = eraser;
        this.events = events;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        UUID householdId = CurrentUser.householdId();
        UUID me = CurrentUser.id();
        Household household = households.findById(householdId)
                .orElseThrow(() -> new NotFoundException("Household não encontrado"));
        List<Member> list = members.findMemberRows(householdId).stream()
                .map(row -> new Member(row.getUserId(), row.getEmail(), HouseholdRole.valueOf(row.getRole()),
                        row.getJoinedAt(), row.getUserId().equals(me)))
                .toList();
        HouseholdRole myRole = list.stream().filter(Member::you).map(Member::role).findFirst()
                .orElseThrow(() -> new ForbiddenException("Você não participa deste household"));
        List<Invite> pending = myRole == HouseholdRole.OWNER ? pendingInvites(householdId) : List.of();
        return new Overview(householdId, household.getName(), myRole, list, pending);
    }

    @Transactional
    public Overview rename(String name) {
        requireOwner();
        households.findById(CurrentUser.householdId()).orElseThrow().rename(name.trim());
        return overview();
    }

    @Transactional
    public InviteCreated invite(String rawEmail) {
        UUID householdId = CurrentUser.householdId();
        requireOwner();
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        if (members.findMemberRows(householdId).stream().anyMatch(m -> m.getEmail().equalsIgnoreCase(email))) {
            throw new ConflictException("Essa pessoa já participa do household");
        }
        List<HouseholdInvite> all = invites.findAllByHouseholdIdOrderByCreatedAtDesc(householdId);
        // Convidar de novo o mesmo e-mail substitui o convite anterior (o link velho para de valer).
        all.stream().filter(i -> i.isPending(now) && i.getEmail().equals(email)).forEach(i -> i.revoke(now));
        long pending = all.stream().filter(i -> i.isPending(now)).count();
        if (members.countByHouseholdId(householdId) + pending + 1 > MAX_MEMBERS) {
            throw new ConflictException("O household pode ter até " + MAX_MEMBERS
                    + " pessoas, contando convites pendentes");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        HouseholdInvite invite = invites.save(new HouseholdInvite(householdId, email, hash(token), CurrentUser.id(),
                now, now.plus(INVITE_VALIDITY)));
        String url = baseUrl + "/convite?token=" + token;
        String householdName = households.findById(householdId).orElseThrow().getName();
        events.publishEvent(new HouseholdInviteCreatedEvent(email, currentEmail(), householdName, url,
                invite.getExpiresAt()));
        return new InviteCreated(toView(invite), url);
    }

    @Transactional
    public void revokeInvite(UUID inviteId) {
        requireOwner();
        invites.findByIdAndHouseholdId(inviteId, CurrentUser.householdId())
                .orElseThrow(() -> new NotFoundException("Convite não encontrado"))
                .revoke(clock.instant());
    }

    @Transactional(readOnly = true)
    public InvitePreview preview(String token) {
        HouseholdInvite invite = usableInvite(token);
        UUID current = CurrentUser.householdId();
        String invitedBy = invite.getInvitedBy() == null ? null
                : members.findEmailByUserId(invite.getInvitedBy()).orElse(null);
        return new InvitePreview(
                households.findById(invite.getHouseholdId()).orElseThrow().getName(),
                invitedBy,
                (int) members.countByHouseholdId(invite.getHouseholdId()),
                invite.getEmail().equalsIgnoreCase(currentEmail()),
                invite.getHouseholdId().equals(current),
                members.countByHouseholdId(current) > 1,
                eraser.hasFinancialData(current));
    }

    /**
     * Entra no household do convite. Devolve o e-mail do usuário para o controller renovar a
     * sessão atual (as outras caem pelo carimbo de segurança).
     */
    @Transactional
    public String accept(String token, boolean discardPersonalData) {
        HouseholdInvite invite = usableInvite(token);
        UUID me = CurrentUser.id();
        UUID current = CurrentUser.householdId();
        String email = currentEmail();
        if (!invite.getEmail().equalsIgnoreCase(email)) {
            throw new ForbiddenException("Este convite foi enviado para outro e-mail. Entre com a conta de "
                    + "quem foi convidado.");
        }
        if (invite.getHouseholdId().equals(current)) {
            throw new ConflictException("Você já participa deste household");
        }
        if (members.countByHouseholdId(current) > 1) {
            throw new ConflictException("Você participa de outro household compartilhado. Saia dele antes "
                    + "(o dono precisa transferir a posse primeiro).");
        }
        if (eraser.hasFinancialData(current) && !discardPersonalData) {
            throw new ConflictException("Seus dados atuais serão apagados ao entrar. Exporte o que quiser "
                    + "guardar e confirme para continuar.");
        }
        if (members.countByHouseholdId(invite.getHouseholdId()) >= MAX_MEMBERS) {
            throw new ConflictException("Este household já está completo");
        }
        lifecycle.erase(current);
        members.flush();
        members.save(new HouseholdMember(invite.getHouseholdId(), me, HouseholdRole.MEMBER));
        invite.accept(me, clock.instant());
        events.publishEvent(new HouseholdMembershipChangedEvent(me, Change.JOINED));
        return email;
    }

    /** Membro sai por conta própria e volta a ter um household pessoal vazio. */
    @Transactional
    public String leave() {
        HouseholdMember membership = currentMembership();
        if (membership.getRole() == HouseholdRole.OWNER) {
            throw new ConflictException(members.countByHouseholdId(membership.getHouseholdId()) > 1
                    ? "Transfira a posse para outro membro antes de sair"
                    : "Você é a única pessoa aqui — não há de onde sair");
        }
        String email = currentEmail();
        detach(membership, email, Change.LEFT);
        return email;
    }

    @Transactional
    public void removeMember(UUID userId) {
        requireOwner();
        if (userId.equals(CurrentUser.id())) {
            throw new ConflictException("Para sair, transfira a posse antes");
        }
        HouseholdMember target = members.findByHouseholdIdAndUserId(CurrentUser.householdId(), userId)
                .orElseThrow(() -> new NotFoundException("Membro não encontrado"));
        detach(target, members.findEmailByUserId(userId).orElseThrow(), Change.REMOVED);
    }

    @Transactional
    public Overview transferOwnership(UUID userId) {
        HouseholdMember me = requireOwner();
        HouseholdMember heir = members.findByHouseholdIdAndUserId(CurrentUser.householdId(), userId)
                .filter(m -> !m.getUserId().equals(me.getUserId()))
                .orElseThrow(() -> new NotFoundException("Membro não encontrado"));
        heir.changeRole(HouseholdRole.OWNER);
        me.changeRole(HouseholdRole.MEMBER);
        return overview();
    }

    private void detach(HouseholdMember membership, String email, Change change) {
        members.delete(membership);
        members.flush();
        lifecycle.createForNewUser(membership.getUserId(), email);
        events.publishEvent(new HouseholdMembershipChangedEvent(membership.getUserId(), change));
    }

    private HouseholdMember currentMembership() {
        return members.findByHouseholdIdAndUserId(CurrentUser.householdId(), CurrentUser.id())
                .orElseThrow(() -> new ForbiddenException("Você não participa deste household"));
    }

    private HouseholdMember requireOwner() {
        HouseholdMember membership = currentMembership();
        if (membership.getRole() != HouseholdRole.OWNER) {
            throw new ForbiddenException("Só o dono do household pode fazer isso");
        }
        return membership;
    }

    private HouseholdInvite usableInvite(String token) {
        return invites.findByTokenHash(hash(token == null ? "" : token))
                .filter(i -> i.isPending(clock.instant()))
                .orElseThrow(() -> new IllegalArgumentException("Este convite é inválido, expirou ou já foi usado"));
    }

    private List<Invite> pendingInvites(UUID householdId) {
        Instant now = clock.instant();
        return invites.findAllByHouseholdIdOrderByCreatedAtDesc(householdId).stream()
                .filter(i -> i.isPending(now))
                .map(HouseholdSharingService::toView)
                .toList();
    }

    private String currentEmail() {
        return members.findEmailByUserId(CurrentUser.id()).orElseThrow();
    }

    private static Invite toView(HouseholdInvite invite) {
        return new Invite(invite.getId(), invite.getEmail(), invite.getCreatedAt(), invite.getExpiresAt());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
