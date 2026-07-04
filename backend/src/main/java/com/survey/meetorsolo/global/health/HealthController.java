package com.survey.meetorsolo.global.health;

import com.survey.meetorsolo.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(new HealthResponse("OK", "meet-or-solo-backend"));
    }

    public record HealthResponse(String status, String service) {
    }
}
