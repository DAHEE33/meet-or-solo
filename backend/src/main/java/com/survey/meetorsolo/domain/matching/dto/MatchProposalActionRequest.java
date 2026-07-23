package com.survey.meetorsolo.domain.matching.dto;

import jakarta.validation.constraints.NotNull;

public record MatchProposalActionRequest(@NotNull Action action) {

    public enum Action {
        ACCEPT,
        REJECT,
        CANCEL_CURRENT_MEMBERS
    }
}
