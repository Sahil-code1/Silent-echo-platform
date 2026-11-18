package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    List<Membership> findByAccountId(String accountId);

    Optional<Membership> findByAccountIdAndOrganisationId(String accountId, String organisationId);
}
