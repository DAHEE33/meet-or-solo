package com.survey.meetorsolo.domain.safety.report.admin.service;

import static org.assertj.core.api.Assertions.*;

import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminReportCursorCodecTest {

    private static final String CURSOR_SECRET_A = "cursor-test-secret-a-with-32-bytes-minimum";
    private static final String CURSOR_SECRET_B = "cursor-test-secret-b-with-32-bytes-minimum";
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-08-15T10:30:45.123456789+09:00");

    @Test
    void 전용_키로_encode_decode하고_createdAt_reportId를_복원한다() {
        AdminReportCursorCodec codec = new AdminReportCursorCodec(CURSOR_SECRET_A);

        String cursor = codec.encode(CREATED_AT, 31L, "filter-a");

        AdminReportCursorCodec.Cursor decoded = codec.decode(cursor, "filter-a");
        assertThat(decoded.createdAt().toInstant()).isEqualTo(CREATED_AT.toInstant());
        assertThat(decoded.reportId()).isEqualTo(31L);
    }

    @Test
    void 다른_cursor_키와_변조_cursor와_filter_불일치는_거절한다() {
        AdminReportCursorCodec issuer = new AdminReportCursorCodec(CURSOR_SECRET_A);
        AdminReportCursorCodec verifier = new AdminReportCursorCodec(CURSOR_SECRET_B);
        String cursor = issuer.encode(CREATED_AT, 31L, "filter-a");
        String tampered = (cursor.startsWith("A") ? "B" : "A") + cursor.substring(1);

        assertThatThrownBy(() -> verifier.decode(cursor, "filter-a"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> issuer.decode(tampered, "filter-a"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> issuer.decode(cursor, "filter-b"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void blank와_32바이트_미만_Secret은_생성_단계에서_거절한다() {
        assertThatThrownBy(() -> new AdminReportCursorCodec(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_REPORT_CURSOR_HMAC_SECRET");
        assertThatThrownBy(() -> new AdminReportCursorCodec("short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트 이상");
    }

    @Test
    void JWT_Secret만_있고_cursor_전용_Secret이_없으면_context_시작에_실패한다() {
        contextRunner()
                .withPropertyValues(
                        "app.jwt.secret=jwt-test-secret-that-is-separate",
                        "app.admin.report.cursor-hmac-secret=${CURSOR_SECRET_INTENTIONALLY_MISSING}")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("CURSOR_SECRET_INTENTIONALLY_MISSING");
                });
    }

    @Test
    void cursor_전용_Secret이_blank이면_context_시작에_실패한다() {
        contextRunner()
                .withPropertyValues("app.admin.report.cursor-hmac-secret= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("ADMIN_REPORT_CURSOR_HMAC_SECRET");
                });
    }

    @Test
    void JWT와_cursor_Secret이_서로_달라도_cursor_context가_시작한다() {
        contextRunner()
                .withPropertyValues(
                        "app.jwt.secret=jwt-test-secret-that-is-separate",
                        "app.admin.report.cursor-hmac-secret=" + CURSOR_SECRET_A)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AdminReportCursorCodec.class);
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withBean(AdminReportCursorCodec.class);
    }
}
