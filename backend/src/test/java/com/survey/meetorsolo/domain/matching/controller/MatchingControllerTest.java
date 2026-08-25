package com.survey.meetorsolo.domain.matching.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupMemberResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupFestivalResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchEventActorResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupEventResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupEventsResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchCancellationReason;
import com.survey.meetorsolo.domain.matching.dto.MatchCancellationResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchingRestrictionResponse;
import com.survey.meetorsolo.domain.matching.service.MatchGroupEventQueryService;
import com.survey.meetorsolo.domain.matching.service.MatchGroupQueryService;
import com.survey.meetorsolo.domain.matching.service.MatchArrivalTimeService;
import com.survey.meetorsolo.domain.matching.service.MatchArrivalService;
import com.survey.meetorsolo.domain.matching.service.MatchPoolCancellationService;
import com.survey.meetorsolo.domain.matching.service.MatchCancellationService;
import com.survey.meetorsolo.domain.matching.service.MatchPoolEntryService;
import com.survey.meetorsolo.domain.matching.service.MatchProposalActionService;
import com.survey.meetorsolo.domain.matching.service.MatchingQueryService;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchingController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class MatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private MatchPoolEntryService poolEntries;

    @MockitoBean
    private MatchingQueryService queries;

    @MockitoBean
    private MatchGroupQueryService groupQueries;

    @MockitoBean
    private MatchGroupEventQueryService groupEventQueries;

    @MockitoBean
    private MatchArrivalTimeService arrivalTimes;

    @MockitoBean
    private MatchArrivalService arrivals;

    @MockitoBean
    private MatchProposalActionService proposalActions;

    @MockitoBean
    private MatchCancellationService cancellations;

    @MockitoBean
    private MatchPoolCancellationService poolCancellations;

    @Test
    void 인증_쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/matching/pools/me/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 잘못된_access_token은_401을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/api/matching/pools/me/current")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "invalid-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void access_token에서_얻은_회원_ID로_pool을_생성한다() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T15:00:00+09:00");
        MatchPoolEntryRequest request = new MatchPoolEntryRequest(10L, 2, false, List.of());
        MatchPoolResponse response = new MatchPoolResponse(
                30L, 10L, 2, false, List.of(), "WAITING", now, now.plusSeconds(60), null);
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(poolEntries.enter(20L, request)).thenReturn(response);

        mockMvc.perform(post("/api/matching/pools")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "festivalId": 10,
                                  "preferredGroupSize": 2,
                                  "allowMinimumTwo": false,
                                  "tags": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.poolId").value(30))
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        verify(poolEntries).enter(20L, request);
    }

    @Test
    void 현재_pool이_없으면_200과_null_data를_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(queries.currentPool(20L)).thenReturn(null);

        mockMvc.perform(get("/api/matching/pools/me/current")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 정상_완료_제한을_기존_cooldown과_분리해_반환한다() throws Exception {
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-08-10T12:00:00+09:00");
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(queries.restrictions(20L)).thenReturn(new MatchingRestrictionResponse(
                0,
                new MatchingRestrictionResponse.CooldownResponse(false, null, null, null, 0),
                new MatchingRestrictionResponse.CompletionLockResponse(
                        true, "MATCH_VALIDITY", 24L, startsAt, startsAt.plusHours(1), 1_200),
                startsAt.plusMinutes(40)
        ));

        mockMvc.perform(get("/api/matching/me/restrictions")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cooldown.active").value(false))
                .andExpect(jsonPath("$.data.completionLock.active").value(true))
                .andExpect(jsonPath("$.data.completionLock.reason").value("MATCH_VALIDITY"))
                .andExpect(jsonPath("$.data.completionLock.groupId").value(24))
                .andExpect(jsonPath("$.data.completionLock.remainingSeconds").value(1_200))
                .andExpect(jsonPath("$.data.serverNow").value("2026-08-10T12:40:00+09:00"));
    }

    @Test
    void 본인_current_group과_구조화된_사유로만_취소한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(cancellations.cancel(20L, MatchCancellationReason.TRANSPORTATION_ISSUE))
                .thenReturn(new MatchCancellationResponse(
                        30L, "CANCELLED", "CONFIRMED", true, 2));

        mockMvc.perform(put("/api/matching/groups/me/current/cancellation")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"TRANSPORTATION_ISSUE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupContinues").value(true))
                .andExpect(jsonPath("$.data.currentMemberCount").value(2));

        verify(cancellations).cancel(20L, MatchCancellationReason.TRANSPORTATION_ISSUE);
    }

    @Test
    void 허용하지_않은_취소_사유는_거절한다() throws Exception {
        mockMvc.perform(put("/api/matching/groups/me/current/cancellation")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"FREE_TEXT"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void active_proposal이_없으면_200과_null_data를_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(queries.activeProposal(20L)).thenReturn(null);

        mockMvc.perform(get("/api/matching/proposals/me/active")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 확정_group_조회는_인증_쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/matching/groups/me/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 현재_group이_없으면_200과_null_data를_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(groupQueries.currentGroup(20L)).thenReturn(null);

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void current_group_event_조회는_인증_쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/matching/groups/me/current/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void access_token_회원의_current_group_event를_안전한_DTO로_반환한다() throws Exception {
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-30T09:01:00+09:00");
        MatchGroupEventsResponse response = new MatchGroupEventsResponse(List.of(
                new MatchGroupEventResponse(
                        102L,
                        "ARRIVAL_TIME_SELECTED",
                        occurredAt,
                        new MatchEventActorResponse(20L, "민수"),
                        10
                )
        ));
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(groupEventQueries.currentGroupEvents(20L)).thenReturn(response);

        mockMvc.perform(get("/api/matching/groups/me/current/events")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].eventId").value(102))
                .andExpect(jsonPath("$.data.events[0].type").value("ARRIVAL_TIME_SELECTED"))
                .andExpect(jsonPath("$.data.events[0].actor.memberId").value(20))
                .andExpect(jsonPath("$.data.events[0].actor.nickname").value("민수"))
                .andExpect(jsonPath("$.data.events[0].arrivalMinutes").value(10))
                .andExpect(jsonPath("$.data.events[0].payload").doesNotExist());

        verify(groupEventQueries).currentGroupEvents(20L);
    }

    @Test
    void current_group이_없으면_event_조회도_200과_null_data를_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(groupEventQueries.currentGroupEvents(20L)).thenReturn(null);

        mockMvc.perform(get("/api/matching/groups/me/current/events")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void group_ID를_직접_지정하는_조회_경로는_제공하지_않는다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);

        mockMvc.perform(get("/api/matching/groups/10")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 도착_예정_시간_선택은_인증_쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/matching/groups/me/current/arrival-time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalMinutes\":10}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 도착_예정_시간_누락과_허용되지_않은_값은_validation_오류다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);

        for (String body : List.of(
                "{}",
                "{\"arrivalMinutes\":0}",
                "{\"arrivalMinutes\":-1}",
                "{\"arrivalMinutes\":1}",
                "{\"arrivalMinutes\":15}",
                "{\"arrivalMinutes\":30}",
                "{\"arrivalMinutes\":60}"
        )) {
            mockMvc.perform(put("/api/matching/groups/me/current/arrival-time")
                            .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 허용된_도착_예정_시간은_로그인_회원_기준으로_갱신된_snapshot을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        MatchGroupResponse response = arrivalGroupResponse();
        for (int minutes : List.of(5, 10, 20, 25)) {
            when(arrivalTimes.select(1L, minutes)).thenReturn(response);

            mockMvc.perform(put("/api/matching/groups/me/current/arrival-time")
                            .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"arrivalMinutes\":" + minutes + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groupId").value(10))
                    .andExpect(jsonPath("$.data.members[0].status")
                            .value("ARRIVAL_TIME_SELECTED"))
                    .andExpect(jsonPath("$.data.members[0].arrivalMinutes").value(10))
                    .andExpect(jsonPath("$.data.members[0].arrivalTimeSelectedAt")
                            .value("2026-07-27T12:35:00+09:00"));
        }
    }

    @Test
    void 도착_마감_오류는_내부_리소스_정보없이_409로_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        when(arrivalTimes.select(1L, 25))
                .thenThrow(new BusinessException(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED));

        mockMvc.perform(put("/api/matching/groups/me/current/arrival-time")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalMinutes\":25}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("MATCHING_ARRIVAL_DEADLINE_EXCEEDED"))
                .andExpect(jsonPath("$.error.message")
                        .value("도착 예정 시간을 최종 마감 안으로 선택해주세요."));
    }

    @Test
    void 도착_완료는_body_없이_로그인_회원_기준_snapshot을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        MatchGroupResponse response = arrivalGroupResponse();
        when(arrivals.arrive(1L)).thenReturn(response);

        mockMvc.perform(put("/api/matching/groups/me/current/arrival")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(10));

        verify(arrivals).arrive(1L);
    }

    @Test
    void 도착_완료는_인증_쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/matching/groups/me/current/arrival"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void access_token의_회원_ID로_현재_group과_참여자를_조회한다() throws Exception {
        OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-07-27T12:30:00+09:00");
        MatchGroupResponse response = new MatchGroupResponse(
                10L,
                1L,
                "CONFIRMED",
                2,
                confirmedAt,
                new MatchGroupFestivalResponse(
                        1L,
                        "테스트 축제",
                        "강원특별자치도 춘천시",
                        java.time.LocalDate.parse("2026-07-27"),
                        java.time.LocalDate.parse("2026-07-29")
                ),
                List.of(
                        new MatchGroupMemberResponse(1L, "member-a", null, "JOINED", null, null),
                        new MatchGroupMemberResponse(
                                2L,
                                "member-b",
                                "https://example.com/b.png",
                                "ARRIVED",
                                10,
                                confirmedAt
                        )
                )
        );
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        when(groupQueries.currentGroup(1L)).thenReturn(response);

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupId").value(10))
                .andExpect(jsonPath("$.data.festivalId").value(1))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedMemberCount").value(2))
                .andExpect(jsonPath("$.data.confirmedAt").value("2026-07-27T12:30:00+09:00"))
                .andExpect(jsonPath("$.data.arrivalDeadlineAt")
                        .value("2026-07-27T13:00:00+09:00"))
                .andExpect(jsonPath("$.data.festival.festivalId").value(1))
                .andExpect(jsonPath("$.data.festival.title").value("테스트 축제"))
                .andExpect(jsonPath("$.data.festival.address").value("강원특별자치도 춘천시"))
                .andExpect(jsonPath("$.data.festival.eventStartDate").value("2026-07-27"))
                .andExpect(jsonPath("$.data.festival.eventEndDate").value("2026-07-29"))
                .andExpect(jsonPath("$.data.members[0].memberId").value(1))
                .andExpect(jsonPath("$.data.members[0].nickname").value("member-a"))
                .andExpect(jsonPath("$.data.members[0].profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].status").value("JOINED"))
                .andExpect(jsonPath("$.data.members[1].memberId").value(2))
                .andExpect(jsonPath("$.data.members[1].nickname").value("member-b"))
                .andExpect(jsonPath("$.data.members[1].profileImageUrl")
                        .value("https://example.com/b.png"))
                .andExpect(jsonPath("$.data.members[1].status").value("ARRIVED"));

        verify(groupQueries).currentGroup(1L);
    }

    private MatchGroupResponse arrivalGroupResponse() {
        OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-07-27T12:30:00+09:00");
        return new MatchGroupResponse(
                10L,
                1L,
                "CONFIRMED",
                2,
                confirmedAt,
                new MatchGroupFestivalResponse(
                        1L,
                        "테스트 축제",
                        "강원특별자치도 춘천시",
                        java.time.LocalDate.parse("2026-07-27"),
                        java.time.LocalDate.parse("2026-07-29")
                ),
                List.of(
                        new MatchGroupMemberResponse(
                                1L,
                                "member-a",
                                null,
                                "ARRIVAL_TIME_SELECTED",
                                10,
                                OffsetDateTime.parse("2026-07-27T12:35:00+09:00")
                        ),
                        new MatchGroupMemberResponse(
                                2L,
                                "member-b",
                                null,
                                "JOINED",
                                null,
                                null
                        )
                )
        );
    }

    @Test
    void 정의되지_않은_action은_400을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);

        mockMvc.perform(post("/api/matching/proposals/1/responses")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START_WITH_CURRENT_MEMBERS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 비어있지_않은_tags는_400을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);

        mockMvc.perform(post("/api/matching/pools")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "festivalId": 10,
                                  "preferredGroupSize": 2,
                                  "allowMinimumTwo": false,
                                  "tags": ["PHOTO"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("tags"));
    }

    @Test
    void 기존_응답_변경_충돌은_409_공통_응답이다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(proposalActions.respond(
                20L,
                1L,
                com.survey.meetorsolo.domain.matching.dto.MatchProposalActionRequest.Action.REJECT
        )).thenThrow(new BusinessException(ErrorCode.MATCHING_CONFLICT));

        mockMvc.perform(post("/api/matching/proposals/1/responses")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_CONFLICT"));
    }
}
