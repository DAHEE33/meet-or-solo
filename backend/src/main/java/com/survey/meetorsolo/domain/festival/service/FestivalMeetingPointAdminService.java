package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.dto.*;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPoint;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalMeetingPointRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalMeetingPointAdminService {
    private final MemberRepository members;
    private final FestivalRepository festivals;
    private final FestivalMeetingPointRepository points;

    public FestivalMeetingPointAdminService(MemberRepository members, FestivalRepository festivals,
            FestivalMeetingPointRepository points) {
        this.members = members;
        this.festivals = festivals;
        this.points = points;
    }

    @Transactional(readOnly = true)
    public List<FestivalMeetingPointResponse> list(long adminId, long festivalId) {
        requireAdmin(adminId);
        if (!festivals.existsById(festivalId)) throw notFound();
        return points.findAllByFestivalIdOrderByAssignmentOrderAscIdAsc(festivalId).stream()
                .map(FestivalMeetingPointResponse::from).toList();
    }

    @Transactional
    public FestivalMeetingPointResponse create(long adminId, long festivalId,
            FestivalMeetingPointUpsertRequest request) {
        requireAdmin(adminId);
        festivals.findByIdForUpdate(festivalId).orElseThrow(this::notFound);
        FestivalMeetingPoint point = FestivalMeetingPoint.inactive(festivalId,
                request.kakaoPlaceId(), request.name(), request.address(), request.longitude(),
                request.latitude(), request.assignmentOrder());
        return FestivalMeetingPointResponse.from(points.saveAndFlush(point));
    }

    @Transactional
    public FestivalMeetingPointResponse update(long adminId, long festivalId, long pointId,
            FestivalMeetingPointUpsertRequest request) {
        requireAdmin(adminId);
        festivals.findByIdForUpdate(festivalId).orElseThrow(this::notFound);
        FestivalMeetingPoint point = requirePoint(festivalId, pointId);
        point.update(request.kakaoPlaceId(), request.name(), request.address(), request.longitude(),
                request.latitude(), request.assignmentOrder());
        return FestivalMeetingPointResponse.from(points.saveAndFlush(point));
    }

    @Transactional
    public FestivalMeetingPointResponse changeStatus(long adminId, long festivalId, long pointId,
            FestivalMeetingPointStatus status) {
        requireAdmin(adminId);
        festivals.findByIdForUpdate(festivalId).orElseThrow(this::notFound);
        FestivalMeetingPoint point = requirePoint(festivalId, pointId);
        point.changeStatus(status);
        return FestivalMeetingPointResponse.from(points.saveAndFlush(point));
    }

    private FestivalMeetingPoint requirePoint(long festivalId, long pointId) {
        FestivalMeetingPoint point = points.findByIdForUpdate(pointId).orElseThrow(this::notFound);
        if (!point.getFestivalId().equals(festivalId)) throw notFound();
        return point;
    }

    private void requireAdmin(long adminId) {
        Member member = members.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!Member.ROLE_ADMIN.equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }
}
