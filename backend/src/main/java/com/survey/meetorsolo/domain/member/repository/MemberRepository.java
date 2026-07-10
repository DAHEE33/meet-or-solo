package com.survey.meetorsolo.domain.member.repository;

import com.survey.meetorsolo.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByProviderAndProviderUserId(String provider, String providerUserId);
}
