package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.OrganisationRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface OrganisationRecoveryCodeRepository extends JpaRepository<OrganisationRecoveryCode, Long> {

    Optional<OrganisationRecoveryCode> findByOrganisationIdAndCodeDigestAndUsedAtIsNull(
            String organisationId, String codeDigest);

    List<OrganisationRecoveryCode> findAllByOrganisationIdAndUsedAtIsNull(String organisationId);
}
