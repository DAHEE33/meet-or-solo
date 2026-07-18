package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.entity.Festival;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalRepository extends JpaRepository<Festival, Long> {

    List<Festival> findAllByContentIdIn(Collection<String> contentIds);

    Optional<Festival> findByContentId(String contentId);
}
