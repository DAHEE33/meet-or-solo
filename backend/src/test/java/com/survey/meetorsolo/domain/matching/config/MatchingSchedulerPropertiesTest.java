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

    /**
     * {@code enabled} 기본값이 {@code true}다.
     *
     * <p>예전에는 {@code false}였다. 매칭 신청 직후 즉시 조합하는 경로가 따로 있어서 이 값이
     * 꺼져 있어도 매칭이 되는 것처럼 보였기 때문이다. 그 경로를 제거한 뒤에는 이 값이 꺼지면
     * 매칭이 성사되지 않고 proposal도 닫히지 않으므로, 기본값을 켜 둔다.
     * 배치가 데이터를 건드리면 곤란한 테스트는 각 테스트에서 {@code false}로 덮어쓴다.
     */
    @Test void application_yml_기본값을_binding한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            MatchingSchedulerProperties properties = context.getBean(MatchingSchedulerProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.fixedDelay()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.staleTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.proposalTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.batchSize()).isEqualTo(20);
        });
    }

    @Test void property_source로_기본값을_override한다() {
        contextRunner.withPropertyValues(
                // 기본값이 true이므로 override 검증은 false 방향으로 한다.
                // 테스트가 배치를 끄는 경로가 실제로 이 property다.
                "app.matching.scheduler.enabled=false",
                "app.matching.scheduler.fixed-delay=7s",
                "app.matching.scheduler.stale-timeout=45s",
                "app.matching.scheduler.proposal-timeout=40s",
                "app.matching.scheduler.batch-size=12"
        ).run(context -> {
            MatchingSchedulerProperties properties = context.getBean(MatchingSchedulerProperties.class);
            assertThat(properties.enabled()).isFalse();
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
