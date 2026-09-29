package com.mecfin.identity.api;

import com.mecfin.identity.application.AccountSecurityService;
import com.mecfin.identity.application.ClientInfo;
import com.mecfin.identity.application.DataExportService;
import com.mecfin.identity.application.MfaService;
import com.mecfin.identity.application.SecurityEventService;
import com.mecfin.identity.application.SessionService;
import com.mecfin.identity.domain.SecurityEventType;
import com.mecfin.identity.domain.User;
import com.mecfin.shared.exception.NotFoundException;
import com.mecfin.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Segurança da própria conta (Fase 14). Operações que mudam o carimbo de segurança (senha,
 * 2FA, sair das outras sessões) renovam a sessão ATUAL em seguida - só as outras caem.
 */
@RestController
@RequestMapping("/account")
public class AccountSecurityController {

    private final AccountSecurityService accountSecurityService;
    private final MfaService mfaService;
    private final SecurityEventService securityEvents;
    private final SessionEstablisher sessionEstablisher;
    private final SessionService sessionService;
    private final DataExportService dataExportService;

    public AccountSecurityController(AccountSecurityService accountSecurityService, MfaService mfaService,
            SecurityEventService securityEvents, SessionEstablisher sessionEstablisher, SessionService sessionService,
            DataExportService dataExportService) {
        this.dataExportService = dataExportService;
        this.accountSecurityService = accountSecurityService;
        this.mfaService = mfaService;
        this.securityEvents = securityEvents;
        this.sessionEstablisher = sessionEstablisher;
        this.sessionService = sessionService;
    }

    /** Sessões ativas (Fase 18). O id devolvido é uma impressão digital, nunca o id real. */
    @GetMapping("/sessions")
    public List<SessionService.SessionInfo> sessions(HttpServletRequest httpRequest) {
        return sessionService.list(currentEmail(), httpRequest.getSession().getId());
    }

    @DeleteMapping("/sessions/{fingerprint}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endSession(@PathVariable String fingerprint, HttpServletRequest httpRequest) {
        if (!sessionService.delete(currentEmail(), fingerprint, httpRequest.getSession().getId())) {
            throw new NotFoundException("Sessão não encontrada");
        }
        securityEvents.record(CurrentUser.id(), SecurityEventType.SESSION_ENDED, AuthController.client(httpRequest));
    }

    @GetMapping("/security")
    public SecurityOverviewResponse overview() {
        User user = accountSecurityService.load(CurrentUser.id());
        return new SecurityOverviewResponse(user.isTotpEnabled(), mfaService.remainingRecoveryCodes(user.getId()),
                user.getPasswordChangedAt(), user.getCreatedAt());
    }

    @GetMapping("/security-events")
    public List<SecurityEventResponse> events() {
        return securityEvents.recent(CurrentUser.id()).stream().map(SecurityEventResponse::from).toList();
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        UUID userId = CurrentUser.id();
        accountSecurityService.changePassword(userId, request.currentPassword(), request.newPassword(),
                AuthController.client(httpRequest));
        refreshSession(httpRequest, httpResponse, userId);
    }

    @PostMapping("/sessions/revoke-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOtherSessions(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UUID userId = CurrentUser.id();
        accountSecurityService.revokeOtherSessions(userId, AuthController.client(httpRequest));
        refreshSession(httpRequest, httpResponse, userId);
    }

    @PostMapping("/2fa/setup")
    public MfaSetupResponse setupMfa() {
        MfaService.Setup setup = mfaService.setup(CurrentUser.id());
        return new MfaSetupResponse(setup.secret(), setup.otpauthUri());
    }

    @PostMapping("/2fa/enable")
    public RecoveryCodesResponse enableMfa(@Valid @RequestBody MfaCodeRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        UUID userId = CurrentUser.id();
        List<String> codes = mfaService.enable(userId, request.code(), AuthController.client(httpRequest));
        refreshSession(httpRequest, httpResponse, userId);
        return new RecoveryCodesResponse(codes);
    }

    @PostMapping("/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableMfa(@Valid @RequestBody ReauthenticationRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        UUID userId = CurrentUser.id();
        ClientInfo client = AuthController.client(httpRequest);
        accountSecurityService.reauthenticate(userId, request.password(), request.code(), client);
        mfaService.disable(userId, client);
        refreshSession(httpRequest, httpResponse, userId);
    }

    @PostMapping("/2fa/recovery-codes")
    public RecoveryCodesResponse regenerateRecoveryCodes(@Valid @RequestBody ReauthenticationRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = CurrentUser.id();
        ClientInfo client = AuthController.client(httpRequest);
        accountSecurityService.reauthenticate(userId, request.password(), request.code(), client);
        return new RecoveryCodesResponse(mfaService.regenerateRecoveryCodes(userId, client));
    }

    /**
     * Portabilidade (LGPD art. 18, V): todos os dados da conta e do household num JSON. POST com
     * a senha no corpo pelo mesmo motivo da exclusão; no-store porque é o arquivo mais sensível
     * que o app gera.
     */
    @PostMapping("/export")
    public ResponseEntity<String> exportData(@Valid @RequestBody ReauthenticationRequest request,
            HttpServletRequest httpRequest) {
        String json = dataExportService.export(CurrentUser.id(), request.password(), request.code(),
                AuthController.client(httpRequest));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("fin-mec-meus-dados-" + LocalDate.now() + ".json").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(json);
    }

    /**
     * Exclusão definitiva da conta e dos dados (LGPD). Encerra a sessão atual. POST em vez de
     * DELETE com corpo: corpo em DELETE é descartado por alguns proxies e clientes HTTP.
     */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@Valid @RequestBody ReauthenticationRequest request, HttpServletRequest httpRequest) {
        String email = currentEmail();
        accountSecurityService.deleteAccount(CurrentUser.id(), request.password(), request.code(),
                AuthController.client(httpRequest));
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        sessionService.deleteAll(email);
    }

    private String currentEmail() {
        return accountSecurityService.load(CurrentUser.id()).getEmail();
    }

    private void refreshSession(HttpServletRequest request, HttpServletResponse response, UUID userId) {
        sessionEstablisher.refresh(request, response, accountSecurityService.load(userId).getEmail());
    }
}
