package com.silentecho.silentecho_backend.security;

import com.silentecho.silentecho_backend.entity.AccountType;
import com.silentecho.silentecho_backend.entity.Complaint;
import com.silentecho.silentecho_backend.entity.Membership;
import com.silentecho.silentecho_backend.entity.MembershipRole;
import com.silentecho.silentecho_backend.exception.ApiException;
import com.silentecho.silentecho_backend.repository.ComplaintRepository;
import com.silentecho.silentecho_backend.repository.MembershipRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * The single place where case-level authorisation is decided.
 *
 * <p>Every lookup is scoped in the query itself, so a valid case ID alone never grants access.
 * A case the caller may not see returns 404, the same as a case that does not exist. This
 * prevents enumeration and insecure direct object references.
 */
@Component
public class CaseAccess {

    private final ComplaintRepository complaints;
    private final MembershipRepository memberships;

    public CaseAccess(ComplaintRepository complaints, MembershipRepository memberships) {
        this.complaints = complaints;
        this.memberships = memberships;
    }

    public Complaint forReporter(AuthenticatedAccount principal, String caseId) {
        if (principal.accountType() != AccountType.PERSONAL) {
            throw notFound();
        }
        return complaints.findByCaseIdAndReporterAccountId(caseId, principal.accountId())
                .orElseThrow(CaseAccess::notFound);
    }

    public Complaint forResponder(AuthenticatedAccount principal, String caseId) {
        if (principal.accountType() != AccountType.ORGANISATION) {
            throw notFound();
        }
        List<String> organisationIds = organisationIds(principal);
        if (organisationIds.isEmpty()) {
            throw notFound();
        }
        return complaints.findByCaseIdAndOrganisationIdIn(caseId, organisationIds)
                .orElseThrow(CaseAccess::notFound);
    }

    public List<String> organisationIds(AuthenticatedAccount principal) {
        return memberships.findByAccountId(principal.accountId()).stream()
                .map(Membership::getOrganisationId)
                .toList();
    }

    public Membership requireRole(AuthenticatedAccount principal, String organisationId, MembershipRole... allowed) {
        Membership membership = memberships.findByAccountIdAndOrganisationId(principal.accountId(), organisationId)
                .orElseThrow(() -> new ApiException(403, "You are not a member of this organisation."));
        if (!Arrays.asList(allowed).contains(membership.getRole())) {
            throw new ApiException(403, "Your role does not allow this action.");
        }
        return membership;
    }

    private static ApiException notFound() {
        return new ApiException(404, "Case not found.");
    }
}
