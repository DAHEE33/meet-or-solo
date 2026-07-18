package com.survey.meetorsolo.domain.festival.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(FestivalSyncProperties.class)
public class FestivalSyncConfiguration {
}
