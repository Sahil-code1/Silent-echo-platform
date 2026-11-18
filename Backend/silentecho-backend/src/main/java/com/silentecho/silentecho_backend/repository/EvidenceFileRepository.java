package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.EvidenceFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, Long> {

    List<EvidenceFile> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);

    Optional<EvidenceFile> findByPublicIdAndComplaintId(String publicId, Long complaintId);
}
