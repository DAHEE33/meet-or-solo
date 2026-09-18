package com.survey.meetorsolo.domain.matching.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.domain.matching.dto.MatchEventActorResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupEventResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupEventsResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository.CurrentGroupEventProjection;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MatchGroupEventQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<Integer> ALLOWED_ARRIVAL_MINUTES = Set.of(0, 5, 10, 20, 25, 30);

    private final MatchGroupQueryService groupQueries;
    private final MatchEventRepository events;
    private final ObjectMapper objectMapper;

    public MatchGroupEventQueryService(
            MatchGroupQueryService groupQueries,
            MatchEventRepository events,
            ObjectMapper objectMapper
    ) {
        this.groupQueries = groupQueries;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    public MatchGroupEventsResponse currentGroupEvents(long memberId) {
        MatchGroupResponse currentGroup = groupQueries.currentGroup(memberId);
        if (currentGroup == null) {
            return null;
        }

        List<MatchGroupEventResponse> result = new ArrayList<>();
        for (CurrentGroupEventProjection event
                : events.findLatestCurrentGroupEvents(currentGroup.groupId())) {
            MatchGroupEventResponse response = toResponse(event);
            if (response != null) {
                result.add(response);
            }
        }
        Collections.reverse(result);
        return new MatchGroupEventsResponse(List.copyOf(result));
    }

    private MatchGroupEventResponse toResponse(CurrentGroupEventProjection event) {
        Integer arrivalMinutes = null;
        if ("ARRIVAL_TIME_SELECTED".equals(event.getEventType())) {
            arrivalMinutes = parseArrivalMinutes(event.getPayload());
            if (arrivalMinutes == null) {
                return null;
            }
        }

        MatchEventActorResponse actor = event.getActorMemberId() == null
                ? null
                : new MatchEventActorResponse(event.getActorMemberId(), event.getActorNickname());
        return new MatchGroupEventResponse(
                event.getEventId(),
                event.getEventType(),
                event.getCreatedAt().atZone(SEOUL).toOffsetDateTime(),
                actor,
                arrivalMinutes
        );
    }

    private Integer parseArrivalMinutes(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode value = root == null ? null : root.get("arrivalMinutes");
            if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
                return null;
            }
            int minutes = value.intValue();
            return ALLOWED_ARRIVAL_MINUTES.contains(minutes) ? minutes : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
