package com.survey.meetorsolo.domain.matching.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Configuration;

class MatchingSchedulerPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            })
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test void application_yml_기본값을_binding한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            MatchingSchedulerProperties properties = context.getBean(MatchingSchedulerProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.fixedDelay()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.staleTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.proposalTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.batchSize()).isEqualTo(20);
        });
    }

    @Test void property_source로_기본값을_override한다() {
        contextRunner.withPropertyValues(
                "app.matching.scheduler.enabled=true",
                "app.matching.scheduler.fixed-delay=7s",
                "app.matching.scheduler.stale-timeout=45s",
                "app.matching.scheduler.proposal-timeout=40s",
                "app.matching.scheduler.batch-size=12"
        ).run(context -> {
            MatchingSchedulerProperties properties = context.getBean(MatchingSchedulerProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.fixedDelay()).isEqualTo(Duration.ofSeconds(7));
            assertThat(properties.staleTimeout()).isEqualTo(Duration.ofSeconds(45));
            assertThat(properties.proposalTimeout()).isEqualTo(Duration.ofSeconds(40));
            assertThat(properties.batchSize()).isEqualTo(12);
        });
    }

    @Test void 영초_duration을_거부한다() {
        assertBindingFailure("app.matching.scheduler.fixed-delay=0s");
    }

    @Test void 음수_duration을_거부한다() {
        assertBindingFailure("app.matching.scheduler.stale-timeout=-1s");
    }

    @Test void 영이하_batch_size를_거부한다() {
        assertBindingFailure("app.matching.scheduler.batch-size=0");
        assertBindingFailure("app.matching.scheduler.batch-size=-1");
    }

    @Test void 잘못된_duration_문자열을_거부한다() {
        assertBindingFailure("app.matching.scheduler.proposal-timeout=invalid");
    }

    private void assertBindingFailure(String property) {
        contextRunner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MatchingSchedulerProperties.class)
    static class PropertiesConfiguration {
    }
}
