package com.survey.meetorsolo.domain.admin.dashboard.service;

import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardStatsResponse;
import com.survey.meetorsolo.domain.admin.dashboard.repository.AdminDashboardRepository;
import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    /** 인기 축제 막대 개수. 대시보드 카드 한 장에 들어가는 높이다. */
    static final int POPULAR_FESTIVAL_LIMIT = 5;
    /**
     * 최근 신고·문의를 각각 몇 건 읽을지. 화면은 둘을 합쳐 최신순으로 보여주므로, 한쪽이
     * 최근을 독차지해도 다른 쪽이 밀려나지 않도록 각각 같은 수를 읽는다.
     */
    static final int RECENT_ISSUE_LIMIT = 5;

    private final AdminAuthorizationService authorization;
    private final AdminDashboardRepository dashboard;
    private final Clock clock;

    public AdminDashboardService(
            AdminAuthorizationService authorization,
            AdminDashboardRepository dashboard,
            Clock clock
    ) {
        this.authorization = authorization;
        this.dashboard = dashboard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getStats(long adminMemberId) {
        authorization.requireAdmin(adminMemberId);
        return new AdminDashboardStatsResponse(
                dashboard.countMembers(),
                dashboard.countMatchGroupsConfirmedSince(startOfToday()),
                dashboard.countCheckins(),
                dashboard.findPopularFestivals(POPULAR_FESTIVAL_LIMIT),
                dashboard.findRecentReports(RECENT_ISSUE_LIMIT),
                dashboard.findRecentInquiries(RECENT_ISSUE_LIMIT));
    }

    /**
     * 오늘 0시. {@code Clock} bean이 {@code Asia/Seoul}이라 관리자가 보는 날짜 경계와 같다
     * ({@code MatchingConfiguration#matchingClock}).
     *
     * <p>"최근 24시간"이 아니라 달력 날짜 기준이다. 오전 9시에 본 "오늘 매칭"이 어제 저녁
     * 건까지 포함하면 숫자를 날짜별로 비교할 수 없다.
     */
    private OffsetDateTime startOfToday() {
        return LocalDate.now(clock).atStartOfDay(clock.getZone()).toOffsetDateTime();
    }
}
