package com.survey.meetorsolo.domain.admin.auth.repository;

import com.survey.meetorsolo.domain.admin.auth.entity.AdminCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminCredentialRepository extends JpaRepository<AdminCredential, Long> {

    Optional<AdminCredential> findByUsername(String username);

    Optional<AdminCredential> findByMemberId(Long memberId);
}
