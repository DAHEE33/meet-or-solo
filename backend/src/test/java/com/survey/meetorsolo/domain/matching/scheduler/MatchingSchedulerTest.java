package com.survey.meetorsolo.domain.matching.scheduler;

import static org.mockito.Mockito.verify;

import com.survey.meetorsolo.domain.matching.service.MatchingOrchestrationService;
import com.survey.meetorsolo.domain.matching.config.MatchingSchedulingConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class MatchingSchedulerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MatchingOrchestrationService.class, () -> Mockito.mock(MatchingOrchestrationService.class))
            .withUserConfiguration(SchedulerConfiguration.class);

    @Test void scheduled_method는_orchestration만_호출한다() {
        MatchingOrchestrationService orchestration = Mockito.mock(MatchingOrchestrationService.class);
        new MatchingScheduler(orchestration).run();
        verify(orchestration).runTick();
        Mockito.verifyNoMoreInteractions(orchestration);
    }

    @Test void enabled_false이면_scheduler_bean을_생성하지_않는다() {
        contextRunner.withPropertyValues("app.matching.scheduler.enabled=false")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(MatchingScheduler.class);
                    org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(MatchingSchedulingConfiguration.class);
                    org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(
                            "org.springframework.context.annotation.internalScheduledAnnotationProcessor");
                });
    }

    @Test void enabled_누락이면_scheduler와_scheduling_infrastructure를_생성하지_않는다() {
        contextRunner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(MatchingScheduler.class);
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(MatchingSchedulingConfiguration.class);
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(
                    "org.springframework.context.annotation.internalScheduledAnnotationProcessor");
        });
    }

    @Test void enabled_true이면_scheduler_bean을_생성한다() {
        contextRunner.withPropertyValues("app.matching.scheduler.enabled=true")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(MatchingScheduler.class);
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(MatchingSchedulingConfiguration.class);
                    org.assertj.core.api.Assertions.assertThat(context).hasBean(
                            "org.springframework.context.annotation.internalScheduledAnnotationProcessor");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({MatchingScheduler.class, MatchingSchedulingConfiguration.class})
    static class SchedulerConfiguration { }
}
