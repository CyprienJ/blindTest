package com.blindtest.controller.admin;

import com.blindtest.model.dto.CreateInvitationRequest;
import com.blindtest.model.dto.InvitationResponse;
import com.blindtest.service.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin REST controller for account invitations, the only way to create an account. */
@RestController
@RequestMapping("/api/admin/invitations")
@RequiredArgsConstructor
public class AdminInvitationController {

    private final InvitationService invitationService;

    @GetMapping
    public List<InvitationResponse> listInvitations() {
        return invitationService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse createInvitation(
            @Valid @RequestBody CreateInvitationRequest request,
            Authentication authentication) {
        return invitationService.invite(request.email(), UUID.fromString(authentication.getName()));
    }

    @PostMapping("/{id}/resend")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse resendInvitation(@PathVariable UUID id, Authentication authentication) {
        return invitationService.resend(id, UUID.fromString(authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@PathVariable UUID id) {
        invitationService.revoke(id);
    }
}
