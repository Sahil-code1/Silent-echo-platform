package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationRepository extends JpaRepository<Organisation, String> {

    List<Organisation> findAllByOrderByNameAsc();

    Optional<Organisation> findByNameKey(String nameKey);

    Optional<Organisation> findByOrganisationCode(String organisationCode);
}
