package com.silentecho.silentecho_backend.dto;

import com.silentecho.silentecho_backend.entity.AccountType;
import com.silentecho.silentecho_backend.entity.MembershipRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** {@code workspace} names the page the user signed in from, so the wrong type is rejected clearly. */
    public record LoginRequest(
            @NotBlank @Size(max = 64) String signInId,
            @NotNull AccountType workspace) {
    }

    /** Returned exactly once, when an account is created. The sign-in ID is never stored in clear. */
    public record AccountCreatedResponse(String signInId, AccountType accountType,
                                         List<OrganisationRef> organisations, String organisationRecoveryCode) {
    }

    public record SessionResponse(boolean authenticated, AccountType accountType, List<OrganisationRef> organisations) {
        public static SessionResponse anonymous() {
            return new SessionResponse(false, null, List.of());
        }
    }

    public record CsrfResponse(String headerName, String token) {
    }

    public record OrganisationRef(String id, String name, String organisationCode, MembershipRole role) {
    }
}
