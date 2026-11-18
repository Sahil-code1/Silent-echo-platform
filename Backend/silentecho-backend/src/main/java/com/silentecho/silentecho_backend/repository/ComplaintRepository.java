package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    /** Reporter access: the case must belong to this reporter account. */
    Optional<Complaint> findByCaseIdAndReporterAccountId(String caseId, String reporterAccountId);

    /** Responder access: the case must belong to one of the responder's organisations. */
    Optional<Complaint> findByCaseIdAndOrganisationIdIn(String caseId, Collection<String> organisationIds);

    List<Complaint> findByReporterAccountIdOrderByCreatedAtDesc(String reporterAccountId);

    List<Complaint> findByOrganisationIdInOrderByCreatedAtDesc(Collection<String> organisationIds);
}
