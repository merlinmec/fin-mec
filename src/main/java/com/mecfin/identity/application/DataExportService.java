package com.mecfin.identity.application;

import com.mecfin.household.infra.HouseholdDataExporter;
import com.mecfin.household.infra.HouseholdMemberRepository;
import com.mecfin.identity.domain.SecurityEventType;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Baixar meus dados" (LGPD art. 18, V — Fase 20). Um documento JSON com a conta (lista de
 * colunas PERMITIDAS: senha, segredo do 2FA e carimbo nunca saem), o histórico de segurança do
 * próprio usuário e tudo do household via {@link HouseholdDataExporter}. Exige a senha de novo,
 * como a exclusão: é o arquivo mais sensível que o app gera.
 *
 * <p>O documento é montado como texto: cada pedaço já vem como JSON válido do Postgres e as
 * chaves são fixas, então não há o que escapar.
 */
@Service
public class DataExportService {

    public static final int FORMAT_VERSION = 1;

    private final AccountSecurityService accountSecurityService;
    private final SecurityEventService securityEvents;
    private final HouseholdMemberRepository members;
    private final HouseholdDataExporter exporter;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DataExportService(AccountSecurityService accountSecurityService, SecurityEventService securityEvents,
            HouseholdMemberRepository members, HouseholdDataExporter exporter, JdbcTemplate jdbc, Clock clock) {
        this.accountSecurityService = accountSecurityService;
        this.securityEvents = securityEvents;
        this.members = members;
        this.exporter = exporter;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public String export(UUID userId, String password, String code, ClientInfo client) {
        accountSecurityService.reauthenticate(userId, password, code, client);
        UUID householdId = members.findHouseholdIdByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Usuário sem household"));
        String user = jdbc.queryForObject("""
                SELECT jsonb_build_object('id', id, 'email', email, 'createdAt', created_at,
                    'passwordChangedAt', password_changed_at, 'twoFactorEnabled', totp_enabled)::text
                FROM users WHERE id = ?
                """, String.class, userId);
        String events = jdbc.queryForObject("""
                SELECT coalesce(jsonb_agg(to_jsonb(e) ORDER BY e.created_at), '[]'::jsonb)::text
                FROM security_events e WHERE user_id = ?
                """, String.class, userId);
        securityEvents.record(userId, SecurityEventType.DATA_EXPORTED, client);

        StringBuilder json = new StringBuilder(8192)
                .append("{\"formatVersion\":").append(FORMAT_VERSION)
                .append(",\"exportedAt\":\"").append(clock.instant()).append('"')
                .append(",\"note\":\"Comprovantes: aqui só os dados de cada arquivo; o arquivo em si baixa pelo app.\"")
                .append(",\"user\":").append(user)
                .append(",\"securityEvents\":").append(events)
                .append(",\"household\":{");
        boolean first = true;
        for (Map.Entry<String, String> section : exporter.export(householdId).entrySet()) {
            json.append(first ? "" : ",").append('"').append(section.getKey()).append("\":").append(section.getValue());
            first = false;
        }
        return json.append("}}").toString();
    }
}
