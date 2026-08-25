package com.survey.meetorsolo.domain.matching.config;

import com.survey.meetorsolo.domain.matching.group.MatchGroupComposer;
import com.survey.meetorsolo.domain.matching.scoring.EmbeddingScorer;
import com.survey.meetorsolo.domain.matching.scoring.PairCompatibilityScorer;
import com.survey.meetorsolo.domain.matching.scoring.TravelStyleScorer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MatchingSchedulerProperties.class)
public class MatchingConfiguration {
    @Bean Clock matchingClock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
    @Bean TravelStyleScorer travelStyleScorer() { return new TravelStyleScorer(); }
    @Bean EmbeddingScorer embeddingScorer() { return new EmbeddingScorer(); }

    /**
     * 태그 Jaccard와 임베딩 코사인 유사도의 가중치는 실사용 데이터로 조정할 값이므로 설정으로 주입한다.
     * 두 값의 합은 1이어야 하며, 어긋나면 기동 시점에 실패한다.
     */
    @Bean
    PairCompatibilityScorer pairCompatibilityScorer(
            TravelStyleScorer travelStyleScorer,
            EmbeddingScorer embeddingScorer,
            @Value("${app.matching.scoring.jaccard-weight}") BigDecimal jaccardWeight,
            @Value("${app.matching.scoring.embedding-weight}") BigDecimal embeddingWeight
    ) {
        return new PairCompatibilityScorer(travelStyleScorer, embeddingScorer, jaccardWeight, embeddingWeight);
    }

    @Bean MatchGroupComposer matchGroupComposer(PairCompatibilityScorer pairCompatibilityScorer) {
        return new MatchGroupComposer(pairCompatibilityScorer);
    }
}
