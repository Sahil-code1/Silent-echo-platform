package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.ComplaintMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintMessageRepository extends JpaRepository<ComplaintMessage, Long> {

    List<ComplaintMessage> findByComplaintIdOrderByCreatedAtAscIdAsc(Long complaintId);
}
