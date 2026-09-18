package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalImageRepository extends JpaRepository<FestivalImage, Long> {

    @Query("""
            select image
            from FestivalImage image
            where image.festival.id in :festivalIds
            order by image.festival.id asc, image.displayOrder asc, image.id asc
            """)
    List<FestivalImage> findAllByFestivalIdIn(
            @Param("festivalIds") Collection<Long> festivalIds
    );
}
