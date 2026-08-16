package com.survey.meetorsolo.domain.matching.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.matching.dto.ActiveMatchProposalResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupEventsResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchArrivalTimeRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchCancellationRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchCancellationResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchProposalActionRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchProposalActionResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchingRestrictionResponse;
import com.survey.meetorsolo.domain.matching.service.MatchPoolEntryService;
import com.survey.meetorsolo.domain.matching.service.MatchGroupEventQueryService;
import com.survey.meetorsolo.domain.matching.service.MatchGroupQueryService;
import com.survey.meetorsolo.domain.matching.service.MatchArrivalTimeService;
import com.survey.meetorsolo.domain.matching.service.MatchArrivalService;
import com.survey.meetorsolo.domain.matching.service.MatchCancellationService;
import com.survey.meetorsolo.domain.matching.service.MatchProposalActionService;
import com.survey.meetorsolo.domain.matching.service.MatchingQueryService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final MatchPoolEntryService poolEntries;
    private final MatchingQueryService queries;
    private final MatchGroupQueryService groupQueries;
    private final MatchGroupEventQueryService groupEventQueries;
    private final MatchArrivalTimeService arrivalTimes;
    private final MatchArrivalService arrivals;
    private final MatchProposalActionService proposalActions;
    private final MatchCancellationService cancellations;

    public MatchingController(
            JwtProvider jwtProvider,
            MatchPoolEntryService poolEntries,
            MatchingQueryService queries,
            MatchGroupQueryService groupQueries,
            MatchGroupEventQueryService groupEventQueries,
            MatchArrivalTimeService arrivalTimes,
            MatchArrivalService arrivals,
            MatchProposalActionService proposalActions,
            MatchCancellationService cancellations
    ) {
        this.jwtProvider = jwtProvider;
        this.poolEntries = poolEntries;
        this.queries = queries;
        this.groupQueries = groupQueries;
        this.groupEventQueries = groupEventQueries;
        this.arrivalTimes = arrivalTimes;
        this.arrivals = arrivals;
        this.proposalActions = proposalActions;
        this.cancellations = cancellations;
    }

    @PostMapping("/pools")
    public ResponseEntity<ApiResponse<MatchPoolResponse>> enterPool(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody MatchPoolEntryRequest request
    ) {
        MatchPoolResponse response = poolEntries.enter(memberId(accessToken), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/pools/me/current")
    public ApiResponse<MatchPoolResponse> currentPool(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(queries.currentPool(memberId(accessToken)));
    }

    @GetMapping("/proposals/me/active")
    public ApiResponse<ActiveMatchProposalResponse> activeProposal(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(queries.activeProposal(memberId(accessToken)));
    }

    @GetMapping("/groups/me/current")
    public ApiResponse<MatchGroupResponse> currentGroup(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(groupQueries.currentGroup(memberId(accessToken)));
    }

    @GetMapping("/groups/me/current/events")
    public ApiResponse<MatchGroupEventsResponse> currentGroupEvents(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(groupEventQueries.currentGroupEvents(memberId(accessToken)));
    }

    @PutMapping("/groups/me/current/arrival")
    public ApiResponse<MatchGroupResponse> arrive(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(arrivals.arrive(memberId(accessToken)));
    }

    @PutMapping("/groups/me/current/arrival-time")
    public ApiResponse<MatchGroupResponse> selectArrivalTime(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody MatchArrivalTimeRequest request
    ) {
        return ApiResponse.success(
                arrivalTimes.select(memberId(accessToken), request.arrivalMinutes())
        );
    }

    @PutMapping("/groups/me/current/cancellation")
    public ApiResponse<MatchCancellationResponse> cancelCurrentParticipation(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody MatchCancellationRequest request
    ) {
        return ApiResponse.success(cancellations.cancel(memberId(accessToken), request.reason()));
    }

    @PostMapping("/proposals/{proposalId}/responses")
    public ApiResponse<MatchProposalActionResponse> respond(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long proposalId,
            @Valid @RequestBody MatchProposalActionRequest request
    ) {
        return ApiResponse.success(proposalActions.respond(memberId(accessToken), proposalId, request.action()));
    }

    @GetMapping("/me/restrictions")
    public ApiResponse<MatchingRestrictionResponse> restrictions(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(queries.restrictions(memberId(accessToken)));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
