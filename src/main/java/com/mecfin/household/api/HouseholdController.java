package com.mecfin.household.api;

import com.mecfin.household.application.HouseholdSharingService;
import com.mecfin.household.application.HouseholdSharingService.InviteCreated;
import com.mecfin.household.application.HouseholdSharingService.InvitePreview;
import com.mecfin.household.application.HouseholdSharingService.Overview;
import com.mecfin.identity.api.SessionEstablisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/household")
public class HouseholdController {

    public record RenameRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record InviteRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    // Token no corpo, não na URL: não vai parar em log de acesso nem em histórico de proxy.
    public record TokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    public record AcceptRequest(@NotBlank @Size(max = 100) String token, boolean discardPersonalData) {
    }

    private final HouseholdSharingService sharing;
    private final SessionEstablisher sessionEstablisher;

    public HouseholdController(HouseholdSharingService sharing, SessionEstablisher sessionEstablisher) {
        this.sharing = sharing;
        this.sessionEstablisher = sessionEstablisher;
    }

    @GetMapping
    public Overview overview() {
        return sharing.overview();
    }

    @PutMapping
    public Overview rename(@Valid @RequestBody RenameRequest request) {
        return sharing.rename(request.name());
    }

    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteCreated invite(@Valid @RequestBody InviteRequest request) {
        return sharing.invite(request.email());
    }

    @DeleteMapping("/invites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvite(@PathVariable UUID id) {
        sharing.revokeInvite(id);
    }

    @PostMapping("/invites/preview")
    public InvitePreview preview(@Valid @RequestBody TokenRequest request) {
        return sharing.preview(request.token());
    }

    /** Entra no household e renova a sessão atual já apontando para ele. */
    @PostMapping("/invites/accept")
    public Overview accept(@Valid @RequestBody AcceptRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String email = sharing.accept(request.token(), request.discardPersonalData());
        sessionEstablisher.refresh(httpRequest, httpResponse, email);
        return sharing.overview();
    }

    @PostMapping("/leave")
    public Overview leave(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = sharing.leave();
        sessionEstablisher.refresh(httpRequest, httpResponse, email);
        return sharing.overview();
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID userId) {
        sharing.removeMember(userId);
    }

    @PostMapping("/members/{userId}/owner")
    public Overview transferOwnership(@PathVariable UUID userId) {
        return sharing.transferOwnership(userId);
    }
}
