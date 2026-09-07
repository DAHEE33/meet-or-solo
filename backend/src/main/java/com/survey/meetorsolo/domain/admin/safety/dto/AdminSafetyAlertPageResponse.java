package com.survey.meetorsolo.domain.admin.safety.dto;

import java.util.List;

public record AdminSafetyAlertPageResponse(
        List<AdminSafetyAlertResponse> alerts,
        AdminSafetyAlertPaginationResponse pagination,
        int openCount
) {
}
