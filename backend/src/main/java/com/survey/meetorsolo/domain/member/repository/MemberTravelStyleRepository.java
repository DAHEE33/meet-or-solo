package com.survey.meetorsolo.domain.member.repository;

import com.survey.meetorsolo.domain.member.entity.MemberTravelStyle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberTravelStyleRepository extends JpaRepository<MemberTravelStyle, Long> {

    List<MemberTravelStyle> findAllByMemberIdOrderById(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("delete from MemberTravelStyle style where style.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
