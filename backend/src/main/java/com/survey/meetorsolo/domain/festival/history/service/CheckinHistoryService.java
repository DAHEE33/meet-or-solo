package com.survey.meetorsolo.domain.festival.history.service;

import com.survey.meetorsolo.domain.checkin.CheckinValidityPolicy;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import com.survey.meetorsolo.domain.festival.history.dto.CheckinHistoryItemResponse;
import com.survey.meetorsolo.domain.festival.history.dto.CheckinHistoryPaginationResponse;
import com.survey.meetorsolo.domain.festival.history.dto.CheckinHistoryResponse;
import com.survey.meetorsolo.domain.festival.repository.FestivalCheckinRepository;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 체크인 기록 조회다.
 *
 * <p>만료·취소된 체크인도 목록에 남긴다. 매칭 기록이 취소된 만남을 남기는 것과 같은 이유로,
 * 이 화면의 목적은 "지금 어디에 체크인되어 있는가"가 아니라 "어디에 다녀왔는가"다.
 */
@Service
public class CheckinHistoryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final FestivalCheckinRepository checkins;
    private final CheckinHistoryCursorCodec cursors;
    private final CheckinValidityPolicy validityPolicy;

    public CheckinHistoryService(
            FestivalCheckinRepository checkins,
            CheckinHistoryCursorCodec cursors,
            CheckinValidityPolicy validityPolicy
    ) {
        this.checkins = checkins;
        this.cursors = cursors;
        this.validityPolicy = validityPolicy;
    }

    @Transactional(readOnly = true)
    public CheckinHistoryResponse getMyHistory(long memberId, String cursor, Integer size) {
        int pageSize = normalizeSize(size);
        CheckinHistoryCursorCodec.Cursor decoded = cursor == null || cursor.isBlank()
                ? null
                : cursors.decode(cursor);

        // hasNext 판정을 위해 한 건 더 읽고 응답에서는 pageSize까지만 남긴다.
        List<FestivalCheckinRepository.CheckinHistoryProjection> rows = checkins.findHistoryByMemberId(
                memberId,
                decoded == null ? null : decoded.checkedInAt(),
                decoded == null ? 0L : decoded.checkinId(),
                pageSize + 1);

        boolean hasNext = rows.size() > pageSize;
        List<FestivalCheckinRepository.CheckinHistoryProjection> page =
                hasNext ? rows.subList(0, pageSize) : rows;
        if (page.isEmpty()) {
            return new CheckinHistoryResponse(
                    List.of(), new CheckinHistoryPaginationResponse(pageSize, false, null));
        }

        OffsetDateTime now = SeoulDateTime.now();
        List<CheckinHistoryItemResponse> items = new ArrayList<>(page.size());
        for (FestivalCheckinRepository.CheckinHistoryProjection row : page) {
            OffsetDateTime checkedInAt = row.getCheckedInAt().atOffset(now.getOffset());
            OffsetDateTime expiresAt = row.getExpiresAt().atOffset(now.getOffset());
            items.add(new CheckinHistoryItemResponse(
                    row.getCheckinId(),
                    row.getFestivalId(),
                    row.getFestivalTitle(),
                    row.getFestivalAddress(),
                    row.getDistanceMeters() == null ? 0 : row.getDistanceMeters(),
                    displayStatus(row.getStatus(), checkedInAt, expiresAt, now),
                    checkedInAt,
                    expiresAt));
        }

        CheckinHistoryItemResponse last = items.get(items.size() - 1);
        return new CheckinHistoryResponse(items, new CheckinHistoryPaginationResponse(
                pageSize,
                hasNext,
                hasNext ? cursors.encode(last.checkedInAt(), last.checkinId()) : null));
    }

    /**
     * 화면에 보여줄 상태를 확정한다.
     *
     * <p>저장된 status가 {@code ACTIVE}라도 유효기간이 지났으면 {@code EXPIRED}다. 만료를
     * 기록하는 배치가 없어 DB의 {@code ACTIVE}는 "취소되지 않았다"는 뜻일 뿐이고, 실제 유효
     * 여부는 {@link CheckinValidityPolicy}가 판정한다. 화면이 이 계산을 다시 하면 정책이
     * 갈라지므로 여기서 확정해 내린다.
     */
    private String displayStatus(
            String storedStatus,
            OffsetDateTime checkedInAt,
            OffsetDateTime expiresAt,
            OffsetDateTime now
    ) {
        if (FestivalCheckinStatus.CANCELLED.name().equals(storedStatus)) {
            return FestivalCheckinStatus.CANCELLED.name();
        }
        return validityPolicy.isValid(checkedInAt, expiresAt, now)
                ? FestivalCheckinStatus.ACTIVE.name()
                : FestivalCheckinStatus.EXPIRED.name();
    }

    private static int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
