package com.silentecho.silentecho_backend.service;

import com.silentecho.silentecho_backend.dto.AuthDtos.OrganisationRef;
import com.silentecho.silentecho_backend.dto.OrganisationDtos.OrganisationOption;
import com.silentecho.silentecho_backend.entity.*;
import com.silentecho.silentecho_backend.exception.ApiException;
import com.silentecho.silentecho_backend.repository.InvitationRepository;
import com.silentecho.silentecho_backend.repository.MembershipRepository;
import com.silentecho.silentecho_backend.repository.OrganisationRepository;
import com.silentecho.silentecho_backend.repository.OrganisationRecoveryCodeRepository;
import com.silentecho.silentecho_backend.security.AuthenticatedAccount;
import com.silentecho.silentecho_backend.security.CaseAccess;
import com.silentecho.silentecho_backend.security.SignInIdHasher;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Organisation registration, invitation codes and team joining. */
@Service
public class OrganisationService {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final String INVITATION_PREFIX = "SEJ-";
    private static final String ORGANISATION_CODE_PREFIX = "SE-ORG-";
    private static final String RECOVERY_CODE_PREFIX = "SER-";
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public record Registration(Organisation organisation, AccountService.NewAccount account,
                               MembershipRole role, String recoveryCode) {
    }

    public record IssuedInvitation(String code, Instant expiresAt, String organisationId) {
    }

    public record IssuedRecoveryCode(String code, String organisationCode) {
    }

    private final OrganisationRepository organisations;
    private final OrganisationRecoveryCodeRepository recoveryCodes;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final AccountService accountService;
    private final SignInIdHasher hasher;
    private final CaseAccess access;
    private final AuditService audit;
    private final SecureRandom random;
    private final Clock clock;

    public OrganisationService(OrganisationRepository organisations,
                               OrganisationRecoveryCodeRepository recoveryCodes,
                               MembershipRepository memberships, InvitationRepository invitations,
                               AccountService accountService, SignInIdHasher hasher, CaseAccess access,
                               AuditService audit, SecureRandom random, Clock clock) {
        this.organisations = organisations;
        this.recoveryCodes = recoveryCodes;
        this.memberships = memberships;
        this.invitations = invitations;
        this.accountService = accountService;
        this.hasher = hasher;
        this.access = access;
        this.audit = audit;
        this.random = random;
        this.clock = clock;
    }

    /** Creates the organisation and its first ADMIN account. Registration is self-service. */
    @Transactional
    public Registration registerOrganisation(String name, OrganisationType type) {
        String displayName = Organisation.displayName(name);
        String nameKey = Organisation.normalizeName(displayName);
        if (organisations.findByNameKey(nameKey).isPresent()) {
            throw new ApiException(409, "This organisation is already registered. Sign in with its existing administrator Sign-in ID, or ask an administrator for a team invitation. Do not register it again.");
        }
        Instant now = clock.instant();
        Organisation organisation;
        try {
            organisation = organisations.saveAndFlush(
                    new Organisation(UUID.randomUUID().toString(), displayName, type, generateOrganisationCode(), now));
        } catch (DataIntegrityViolationException duplicateRace) {
            // The unique database key also protects against two simultaneous registration requests.
            throw new ApiException(409, "This organisation is already registered. Sign in with its existing administrator Sign-in ID, or ask an administrator for a team invitation. Do not register it again.");
        }
        AccountService.NewAccount admin = accountService.createAccount(AccountType.ORGANISATION);
        memberships.save(new Membership(admin.account().getId(), organisation.getId(), MembershipRole.ADMIN, now));
        String recoveryCode = generateRecoveryCode();
        recoveryCodes.save(new OrganisationRecoveryCode(organisation.getId(),
                hasher.digestRecoveryCode(recoveryCode), now));
        audit.record(admin.account().getId(), organisation.getId(), null, "ORGANISATION_REGISTERED");
        return new Registration(organisation, admin, MembershipRole.ADMIN, recoveryCode);
    }

    @Transactional
    public IssuedInvitation issueInvitation(AuthenticatedAccount actor, String organisationId) {
        access.requireRole(actor, organisationId, MembershipRole.ADMIN);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(INVITATION_TTL);
        String code = INVITATION_PREFIX + randomToken();
        invitations.save(new Invitation(organisationId, hasher.digestInvitationCode(code),
                actor.accountId(), now, expiresAt));
        audit.record(actor.accountId(), organisationId, null, "INVITATION_ISSUED");
        return new IssuedInvitation(code, expiresAt, organisationId);
    }

    /** Creates a responder account for the organisation the code belongs to. Codes are single-use. */
    @Transactional
    public Registration joinWithInvitation(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        ApiException invalid = new ApiException(400, "This invitation code is invalid, expired or already used.");
        if (!code.startsWith(INVITATION_PREFIX)) {
            throw invalid;
        }
        Instant now = clock.instant();
        Invitation invitation = invitations.findByCodeDigest(hasher.digestInvitationCode(code))
                .orElseThrow(() -> invalid);
        if (!invitation.isUsable(now)) {
            throw invalid;
        }
        Organisation organisation = organisations.findById(invitation.getOrganisationId())
                .orElseThrow(() -> invalid);
        AccountService.NewAccount responder = accountService.createAccount(AccountType.ORGANISATION);
        memberships.save(new Membership(responder.account().getId(), organisation.getId(),
                MembershipRole.RESPONDER, now));
        invitation.markUsed(now);
        audit.record(responder.account().getId(), organisation.getId(), null, "TEAM_MEMBER_JOINED");
        return new Registration(organisation, responder, MembershipRole.RESPONDER, null);
    }

    /** Creates or rotates a one-time recovery secret for an organisation administrator. */
    @Transactional
    public IssuedRecoveryCode issueRecoveryCode(AuthenticatedAccount actor, String organisationId) {
        access.requireRole(actor, organisationId, MembershipRole.ADMIN);
        Organisation organisation = organisations.findById(organisationId)
                .orElseThrow(() -> new ApiException(404, "Organisation not found."));
        Instant now = clock.instant();
        for (OrganisationRecoveryCode previous : recoveryCodes.findAllByOrganisationIdAndUsedAtIsNull(organisationId)) {
            previous.markUsed(now);
        }
        String code = generateRecoveryCode();
        recoveryCodes.save(new OrganisationRecoveryCode(organisationId, hasher.digestRecoveryCode(code), now));
        audit.record(actor.accountId(), organisationId, null, "ORGANISATION_RECOVERY_CODE_ISSUED");
        return new IssuedRecoveryCode(code, organisation.getOrganisationCode());
    }

    /** Uses a one-time recovery secret to create a replacement organisation administrator account. */
    @Transactional
    public Registration recoverOrganisationAdmin(String rawOrganisationCode, String rawRecoveryCode) {
        String organisationCode = rawOrganisationCode == null ? "" : rawOrganisationCode.trim().toUpperCase(java.util.Locale.ROOT);
        String recoveryCode = rawRecoveryCode == null ? "" : rawRecoveryCode.trim();
        ApiException invalid = new ApiException(400, "The organisation code or recovery code is invalid or has already been used.");
        Organisation organisation = organisations.findByOrganisationCode(organisationCode).orElseThrow(() -> invalid);
        OrganisationRecoveryCode savedCode = recoveryCodes
                .findByOrganisationIdAndCodeDigestAndUsedAtIsNull(
                        organisation.getId(), hasher.digestRecoveryCode(recoveryCode))
                .orElseThrow(() -> invalid);

        Instant now = clock.instant();
        savedCode.markUsed(now);
        AccountService.NewAccount replacementAdmin = accountService.createAccount(AccountType.ORGANISATION);
        memberships.save(new Membership(replacementAdmin.account().getId(), organisation.getId(), MembershipRole.ADMIN, now));

        // Rotate the recovery secret. A used recovery code cannot be replayed.
        String nextRecoveryCode = generateRecoveryCode();
        recoveryCodes.save(new OrganisationRecoveryCode(organisation.getId(),
                hasher.digestRecoveryCode(nextRecoveryCode), now));
        audit.record(replacementAdmin.account().getId(), organisation.getId(), null, "ORGANISATION_ADMIN_RECOVERED");
        return new Registration(organisation, replacementAdmin, MembershipRole.ADMIN, nextRecoveryCode);
    }

    @Transactional(readOnly = true)
    public List<OrganisationRef> membershipsOf(String accountId) {
        List<OrganisationRef> refs = new ArrayList<>();
        for (Membership membership : memberships.findByAccountId(accountId)) {
            organisations.findById(membership.getOrganisationId())
                    .ifPresent(org -> refs.add(new OrganisationRef(org.getId(), org.getName(), org.getOrganisationCode(), membership.getRole())));
        }
        return refs;
    }

    @Transactional(readOnly = true)
    public List<OrganisationOption> listOrganisations() {
        return organisations.findAllByOrderByNameAsc().stream()
                .map(org -> new OrganisationOption(org.getId(), org.getName(), org.getType(), org.getOrganisationCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String nameOf(String organisationId) {
        return organisations.findById(organisationId).map(Organisation::getName).orElse("Organisation");
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Public identifier only; never accepted as proof of account ownership or for login. */
    private String generateOrganisationCode() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        StringBuilder suffix = new StringBuilder(bytes.length);
        for (byte value : bytes) {
            suffix.append(CODE_ALPHABET[(value & 0xff) % CODE_ALPHABET.length]);
        }
        return ORGANISATION_CODE_PREFIX + suffix;
    }

    private String generateRecoveryCode() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return RECOVERY_CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
