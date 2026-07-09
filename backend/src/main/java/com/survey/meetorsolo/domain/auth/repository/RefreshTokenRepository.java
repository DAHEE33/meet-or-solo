package com.survey.meetorsolo.domain.auth.repository;

import com.survey.meetorsolo.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
