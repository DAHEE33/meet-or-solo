package com.survey.meetorsolo.global.config;

import java.security.Principal;

public record WebSocketMemberPrincipal(long memberId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
