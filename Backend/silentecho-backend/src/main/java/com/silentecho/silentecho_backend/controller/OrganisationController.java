package com.silentecho.silentecho_backend.controller;

import com.silentecho.silentecho_backend.dto.AuthDtos.AccountCreatedResponse;
import com.silentecho.silentecho_backend.dto.OrganisationDtos.*;
import com.silentecho.silentecho_backend.entity.AccountType;
import com.silentecho.silentecho_backend.security.CurrentAccount;
import com.silentecho.silentecho_backend.service.AuthSessionService;
import com.silentecho.silentecho_backend.service.OrganisationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations")
public class OrganisationController {

    private final OrganisationService organisations;
    private final AuthSessionService sessions;

    public OrganisationController(OrganisationService organisations, AuthSessionService sessions) {
        this.organisations = organisations;
        this.sessions = sessions;
    }

    @GetMapping
    public List<OrganisationOption> list() {
        return organisations.listOrganisations();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountCreatedResponse register(@Valid @RequestBody RegisterOrganisationRequest body,
                                           HttpServletRequest request, HttpServletResponse response) {
        OrganisationService.Registration registration = organisations.registerOrganisation(body.name(), body.type());
        sessions.start(registration.account().account(), request, response);
        return new AccountCreatedResponse(registration.account().signInId(), AccountType.ORGANISATION,
                organisations.membershipsOf(registration.account().account().getId()), registration.recoveryCode());
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountCreatedResponse join(@Valid @RequestBody JoinRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        OrganisationService.Registration registration = organisations.joinWithInvitation(body.invitationCode());
        sessions.start(registration.account().account(), request, response);
        return new AccountCreatedResponse(registration.account().signInId(), AccountType.ORGANISATION,
                organisations.membershipsOf(registration.account().account().getId()), null);
    }


    @PostMapping("/recover")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountCreatedResponse recover(@Valid @RequestBody RecoverOrganisationRequest body,
                                          HttpServletRequest request, HttpServletResponse response) {
        OrganisationService.Registration registration = organisations.recoverOrganisationAdmin(
                body.organisationCode(), body.recoveryCode());
        sessions.start(registration.account().account(), request, response);
        return new AccountCreatedResponse(registration.account().signInId(), AccountType.ORGANISATION,
                organisations.membershipsOf(registration.account().account().getId()), registration.recoveryCode());
    }


    @PostMapping("/recovery-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public RecoveryCodeResponse issueRecoveryCode(@Valid @RequestBody CreateRecoveryCodeRequest body) {
        OrganisationService.IssuedRecoveryCode issued = organisations.issueRecoveryCode(
                CurrentAccount.require(), body.organisationId());
        return new RecoveryCodeResponse(issued.code(), issued.organisationCode());
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@Valid @RequestBody CreateInvitationRequest body) {
        OrganisationService.IssuedInvitation issued =
                organisations.issueInvitation(CurrentAccount.require(), body.organisationId());
        return new InvitationResponse(issued.code(), issued.expiresAt(), issued.organisationId());
    }
}
