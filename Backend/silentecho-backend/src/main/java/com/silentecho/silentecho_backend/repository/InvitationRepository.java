package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByCodeDigest(String codeDigest);
}
