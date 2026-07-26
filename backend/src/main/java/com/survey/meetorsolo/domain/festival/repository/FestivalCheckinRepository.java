package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.entity.FestivalCheckin;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalCheckinRepository extends JpaRepository<FestivalCheckin, Long> {

    List<FestivalCheckin> findAllByMemberIdAndStatus(Long memberId, FestivalCheckinStatus status);
}
