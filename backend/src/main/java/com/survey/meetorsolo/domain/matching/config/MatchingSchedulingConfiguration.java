package com.survey.meetorsolo.domain.matching.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnExpression(
                "'${app.matching.scheduler.enabled:false}' == 'true'"
                + " || '${app.matching.no-show-scheduler.enabled:false}' == 'true'"
                + " || '${app.admin.member.suspension-scheduler-enabled:false}' == 'true'"
)
public class MatchingSchedulingConfiguration {
}
