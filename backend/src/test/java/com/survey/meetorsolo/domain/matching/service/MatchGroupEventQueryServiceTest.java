package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupEventsResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository.CurrentGroupEventProjection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchGroupEventQueryServiceTest {

    private final MatchGroupQueryService groupQueries = mock(MatchGroupQueryService.class);
    private final MatchEventRepository events = mock(MatchEventRepository.class);
    private final MatchGroupEventQueryService service =
            new MatchGroupEventQueryService(groupQueries, events, new ObjectMapper());

    @Test
    void active_group이_없으면_null이고_event를_조회하지_않는다() {
        when(groupQueries.currentGroup(1L)).thenReturn(null);

        assertThat(service.currentGroupEvents(1L)).isNull();
        verifyNoInteractions(events);
    }

    @Test
    void 최신순_projection을_시간_ID_오름차순_DTO로_변환하고_raw_payload를_숨긴다() {
        MatchGroupResponse group = mock(MatchGroupResponse.class);
        when(group.groupId()).thenReturn(10L);
        when(groupQueries.currentGroup(1L)).thenReturn(group);
        CurrentGroupEventProjection arrived = event(
                3L, "MEMBER_ARRIVED", "{}", "2026-07-30T00:02:00Z", 2L, "지연"
        );
        CurrentGroupEventProjection selected = event(
                2L, "ARRIVAL_TIME_SELECTED", "{\"arrivalMinutes\":10}",
                "2026-07-30T00:01:00Z", 1L, "민수"
        );
        CurrentGroupEventProjection confirmed = event(
                1L, "MATCH_CONFIRMED", "{}", "2026-07-30T00:00:00Z", null, null
        );
        when(events.findLatestCurrentGroupEvents(10L))
                .thenReturn(List.of(arrived, selected, confirmed));

        MatchGroupEventsResponse response = service.currentGroupEvents(1L);

        assertThat(response.events()).extracting(event -> event.eventId())
                .containsExactly(1L, 2L, 3L);
        assertThat(response.events().get(0).actor()).isNull();
        assertThat(response.events().get(1).actor().nickname()).isEqualTo("민수");
        assertThat(response.events().get(1).arrivalMinutes()).isEqualTo(10);
        assertThat(response.events().get(1).occurredAt().toString())
                .isEqualTo("2026-07-30T09:01+09:00");
        verify(events).findLatestCurrentGroupEvents(10L);
    }

    @Test
    void 잘못된_도착_예정_payload만_제외하고_나머지_event는_반환한다() {
        MatchGroupResponse group = mock(MatchGroupResponse.class);
        when(group.groupId()).thenReturn(10L);
        when(groupQueries.currentGroup(1L)).thenReturn(group);
        CurrentGroupEventProjection arrived = event(
                3L, "MEMBER_ARRIVED", "{}", "2026-07-30T00:03:00Z", 1L, "민수"
        );
        CurrentGroupEventProjection invalidMinutes = event(
                2L, "ARRIVAL_TIME_SELECTED", "{\"arrivalMinutes\":15}",
                "2026-07-30T00:02:00Z", 1L, "민수"
        );
        CurrentGroupEventProjection malformed = event(
                1L, "ARRIVAL_TIME_SELECTED", "{broken",
                "2026-07-30T00:01:00Z", 1L, "민수"
        );
        when(events.findLatestCurrentGroupEvents(10L))
                .thenReturn(List.of(arrived, invalidMinutes, malformed));

        MatchGroupEventsResponse response = service.currentGroupEvents(1L);

        assertThat(response.events()).singleElement()
                .extracting(event -> event.type())
                .isEqualTo("MEMBER_ARRIVED");
    }

    @Test
    void 과거_0과_30_및_신규_25분_event를_모두_조회한다() {
        MatchGroupResponse group = mock(MatchGroupResponse.class);
        when(group.groupId()).thenReturn(10L);
        when(groupQueries.currentGroup(1L)).thenReturn(group);
        CurrentGroupEventProjection twentyFive = event(
                3L, "ARRIVAL_TIME_SELECTED", "{\"arrivalMinutes\":25}",
                "2026-07-30T00:03:00Z", 1L, "민수"
        );
        CurrentGroupEventProjection thirty = event(
                2L, "ARRIVAL_TIME_SELECTED", "{\"arrivalMinutes\":30}",
                "2026-07-30T00:02:00Z", 1L, "민수"
        );
        CurrentGroupEventProjection zero = event(
                1L, "ARRIVAL_TIME_SELECTED", "{\"arrivalMinutes\":0}",
                "2026-07-30T00:01:00Z", 1L, "민수"
        );
        when(events.findLatestCurrentGroupEvents(10L))
                .thenReturn(List.of(twentyFive, thirty, zero));

        assertThat(service.currentGroupEvents(1L).events())
                .extracting(event -> event.arrivalMinutes())
                .containsExactly(0, 30, 25);
    }

    private CurrentGroupEventProjection event(
            long id,
            String type,
            String payload,
            String createdAt,
            Long actorId,
            String actorNickname
    ) {
        CurrentGroupEventProjection event = mock(CurrentGroupEventProjection.class);
        when(event.getEventId()).thenReturn(id);
        when(event.getEventType()).thenReturn(type);
        when(event.getPayload()).thenReturn(payload);
        when(event.getCreatedAt()).thenReturn(Instant.parse(createdAt));
        when(event.getActorMemberId()).thenReturn(actorId);
        when(event.getActorNickname()).thenReturn(actorNickname);
        return event;
    }
}
