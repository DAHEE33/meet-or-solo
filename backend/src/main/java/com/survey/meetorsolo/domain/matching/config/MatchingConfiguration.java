package com.survey.meetorsolo.domain.matching.config;

import com.survey.meetorsolo.domain.matching.group.MatchGroupComposer;
import com.survey.meetorsolo.domain.matching.scoring.TravelStyleScorer;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MatchingSchedulerProperties.class)
public class MatchingConfiguration {
    @Bean Clock matchingClock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
    @Bean TravelStyleScorer travelStyleScorer() { return new TravelStyleScorer(); }
    @Bean MatchGroupComposer matchGroupComposer(TravelStyleScorer scorer) { return new MatchGroupComposer(scorer); }
}
