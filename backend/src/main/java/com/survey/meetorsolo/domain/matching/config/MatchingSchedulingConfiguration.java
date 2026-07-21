package com.survey.meetorsolo.domain.matching.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.matching.scheduler", name = "enabled", havingValue = "true")
public class MatchingSchedulingConfiguration {
}
