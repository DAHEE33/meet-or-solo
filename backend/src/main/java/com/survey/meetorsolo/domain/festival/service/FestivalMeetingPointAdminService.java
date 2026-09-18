package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.dto.*;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPoint;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.festival.repository.FestivalMeetingPointRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalMeetingPointAdminService {
    private final AdminAuthorizationService authorization;
    private final FestivalRepository festivals;
    private final FestivalMeetingPointRepository points;

    public FestivalMeetingPointAdminService(AdminAuthorizationService authorization,
            FestivalRepository festivals, FestivalMeetingPointRepository points) {
        this.authorization = authorization;
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

    /**
     * 자체 구현 대신 공통 {@link AdminAuthorizationService}에 맡긴다.
     *
     * <p>여기에만 검사를 따로 두면 관리자 자격 규칙이 갈라진다. 실제로 관리자 진입을
     * 로컬 계정으로 한정할 때 이 경로만 열려 있었다 — 소셜 계정에 role만 올리면
     * 만남 장소를 그대로 고칠 수 있었다. 제재 판정이 함께 걸리는 것도 이득이다.
     */
    private void requireAdmin(long adminId) {
        authorization.requireAdmin(adminId);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }
}
