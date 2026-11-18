package com.silentecho.silentecho_backend.dto;

import com.silentecho.silentecho_backend.entity.OrganisationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class OrganisationDtos {

    private OrganisationDtos() {
    }

    public record RegisterOrganisationRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull OrganisationType type) {
    }

    public record JoinRequest(@NotBlank @Size(max = 80) String invitationCode) {
    }

    public record RecoverOrganisationRequest(
            @NotBlank @Size(max = 24) String organisationCode,
            @NotBlank @Size(max = 80) String recoveryCode) {
    }

    public record CreateInvitationRequest(@NotBlank @Size(max = 36) String organisationId) {
    }

    public record CreateRecoveryCodeRequest(@NotBlank @Size(max = 36) String organisationId) {
    }

    public record RecoveryCodeResponse(String recoveryCode, String organisationCode) {
    }

    /** The invitation code is shown once and never stored in clear. */
    public record InvitationResponse(String code, Instant expiresAt, String organisationId) {
    }

    public record OrganisationOption(String id, String name, OrganisationType type, String organisationCode) {
    }
}
