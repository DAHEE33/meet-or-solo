package com.survey.meetorsolo.domain.tourplace.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TourPlaceSyncProperties.class)
public class TourPlaceSyncConfiguration {
}
