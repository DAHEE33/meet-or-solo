package com.survey.meetorsolo.domain.notification.repository;

import com.survey.meetorsolo.domain.notification.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findAllByMemberId(long memberId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);

    void deleteByMemberIdAndEndpoint(long memberId, String endpoint);
}
