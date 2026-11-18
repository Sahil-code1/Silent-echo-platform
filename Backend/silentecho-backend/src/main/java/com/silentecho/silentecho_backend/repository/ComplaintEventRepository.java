package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.ComplaintEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintEventRepository extends JpaRepository<ComplaintEvent, Long> {

    List<ComplaintEvent> findByComplaintIdOrderByCreatedAtAscIdAsc(Long complaintId);
}
