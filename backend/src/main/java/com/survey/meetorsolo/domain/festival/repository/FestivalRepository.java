package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.entity.Festival;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalRepository extends JpaRepository<Festival, Long> {
    @Query(value = "SELECT * FROM festivals WHERE id = :festivalId FOR UPDATE", nativeQuery = true)
    Optional<Festival> findByIdForUpdate(@Param("festivalId") long festivalId);
}
