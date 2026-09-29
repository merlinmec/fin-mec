package com.mecfin.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Sessões ativas do usuário (Fase 18), guardadas no Postgres pelo Spring Session JDBC.
 *
 * O ID de sessão é uma credencial (quem tem o cookie, está logado) — então NUNCA sai para o
 * cliente: a tela recebe só uma impressão digital (SHA-256 truncado) de cada sessão, e encerrar
 * uma sessão é localizar pela impressão digital entre as sessões DO PRÓPRIO usuário.
 */
@Service
public class SessionService {

    public static final String USER_AGENT_ATTRIBUTE = "mecfin.userAgent";
    public static final String IP_ATTRIBUTE = "mecfin.ip";

    public record SessionInfo(String id, String device, String ipAddress, Instant createdAt, Instant lastAccessedAt,
            boolean current) {
    }

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public List<SessionInfo> list(String email, String currentSessionId) {
        return sessions.findByPrincipalName(email).values().stream()
                .map(s -> new SessionInfo(fingerprint(s.getId()),
                        UserAgents.describe(s.getAttribute(USER_AGENT_ATTRIBUTE)),
                        s.getAttribute(IP_ATTRIBUTE), s.getCreationTime(), s.getLastAccessedTime(),
                        s.getId().equals(currentSessionId)))
                .sorted(Comparator.comparing(SessionInfo::current).reversed()
                        .thenComparing(SessionInfo::lastAccessedAt, Comparator.reverseOrder()))
                .toList();
    }

    /** Encerra uma sessão do próprio usuário (nunca a atual — para isso existe "Sair"). */
    public boolean delete(String email, String fingerprint, String currentSessionId) {
        for (Map.Entry<String, ? extends Session> entry : sessions.findByPrincipalName(email).entrySet()) {
            if (fingerprint(entry.getKey()).equals(fingerprint) && !entry.getKey().equals(currentSessionId)) {
                sessions.deleteById(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public void deleteOthers(String email, String currentSessionId) {
        sessions.findByPrincipalName(email).keySet().stream()
                .filter(id -> !id.equals(currentSessionId))
                .toList()
                .forEach(sessions::deleteById);
    }

    public void deleteAll(String email) {
        sessions.findByPrincipalName(email).keySet().stream().toList().forEach(sessions::deleteById);
    }

    static String fingerprint(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
