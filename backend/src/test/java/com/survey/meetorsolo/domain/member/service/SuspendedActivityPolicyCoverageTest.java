package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정지 회원 정책의 분류 누락을 막는 전수 조사.
 *
 * <p>{@link SuspendedActivityPolicy}는 차단 목록 방식이라, 새 활동 endpoint를 만들고 목록에
 * 넣는 것을 잊으면 정지 회원이 그 기능을 조용히 쓸 수 있다. 목록 방식의 단순함은 유지하면서
 * 이 위험만 막기 위해, 상태를 바꾸는 모든 endpoint가 <b>차단이든 허용이든 명시적으로
 * 분류되어 있는지</b>를 여기서 검사한다.
 *
 * <p>이 테스트가 실패하면 새 endpoint가 분류되지 않은 것이다. 정지 회원이 그 요청을 해도
 * 되는지 판단해서 {@code RESTRICTED} 또는 {@code ALLOWED}에 넣어야 한다.
 */
class SuspendedActivityPolicyCoverageTest {

    private static final String CONTROLLER_PACKAGE = "com.survey.meetorsolo.domain";

    /** 상태를 바꾸는 요청. 조회(GET)는 정지 회원에게 모두 허용이므로 검사 대상이 아니다. */
    private static final List<Class<? extends java.lang.annotation.Annotation>> WRITE_MAPPINGS =
            List.of(PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private final SuspendedActivityPolicy policy = new SuspendedActivityPolicy();

    @Test
    void 상태를_바꾸는_모든_endpoint가_차단_또는_허용으로_분류되어_있다() {
        List<Endpoint> unclassified = writeEndpoints().stream()
                .filter(endpoint -> !policy.isRestricted(endpoint.method(), endpoint.samplePath()))
                .filter(endpoint -> !policy.isExplicitlyAllowed(endpoint.method(), endpoint.samplePath()))
                .toList();

        assertThat(unclassified)
                .as("""
                        정지 회원 정책에 분류되지 않은 endpoint가 있습니다.
                        정지 회원이 이 요청을 해도 되는지 판단해서 SuspendedActivityPolicy의
                        RESTRICTED(차단) 또는 ALLOWED(허용)에 추가하세요.""")
                .isEmpty();
    }

    @Test
    void 사용자가_지정한_활동은_차단_목록에_있다() {
        assertThat(policy.isRestricted("POST", "/api/festivals/144/checkin")).isTrue();
        assertThat(policy.isRestricted("POST", "/api/matching/pools")).isTrue();
        assertThat(policy.isRestricted("POST", "/api/matching/proposals/7/responses")).isTrue();
        assertThat(policy.isRestricted("POST", "/api/festivals/144/comments")).isTrue();
        assertThat(policy.isRestricted("POST", "/api/spots/12/comments")).isTrue();
        assertThat(policy.isRestricted("PUT", "/api/comments/9/like")).isTrue();
    }

    @Test
    void 조회는_차단하지_않는다() {
        assertThat(policy.isRestricted("GET", "/api/festivals")).isFalse();
        assertThat(policy.isRestricted("GET", "/api/festivals/144")).isFalse();
        assertThat(policy.isRestricted("GET", "/api/festivals/144/comments")).isFalse();
        assertThat(policy.isRestricted("GET", "/api/members/me")).isFalse();
        assertThat(policy.isRestricted("GET", "/api/members/me/match-history")).isFalse();
    }

    /** 안전 기능은 정지 중에도 막지 않는다. 정지는 신고·차단 권리를 박탈하는 조치가 아니다. */
    @Test
    void 신고와_차단은_차단하지_않는다() {
        assertThat(policy.isRestricted("POST", "/api/match-groups/31/reports")).isFalse();
        assertThat(policy.isRestricted("POST", "/api/match-groups/31/blocks")).isFalse();
        assertThat(policy.isRestricted("DELETE", "/api/members/me/blocks/27")).isFalse();
    }

    /** 동의 철회는 개인정보 권리이므로 제재로 막을 수 없다. */
    @Test
    void 동의_철회는_차단하지_않는다() {
        assertThat(policy.isRestricted("POST", "/api/members/me/consents")).isFalse();
        assertThat(policy.isRestricted("DELETE", "/api/members/me/consents/AI_PROCESSING")).isFalse();
    }

    @Test
    void 체크인_취소는_체크인_차단_규칙에_걸리지_않는다() {
        // "/api/festivals/*/checkin"이 "/api/festivals/checkin/me"까지 잡으면 정리 행위가 막힌다.
        assertThat(policy.isRestricted("DELETE", "/api/festivals/checkin/me")).isFalse();
        assertThat(policy.isExplicitlyAllowed("DELETE", "/api/festivals/checkin/me")).isTrue();
    }

    @Test
    void method가_다르면_차단하지_않는다() {
        // 같은 경로라도 조회는 허용이어야 한다.
        assertThat(policy.isRestricted("GET", "/api/matching/pools")).isFalse();
        assertThat(policy.isRestricted("DELETE", "/api/comments/9/like")).isFalse();
    }

    /**
     * {@code @RestController}를 훑어 상태 변경 endpoint를 모은다.
     *
     * <p>관리자 경로는 제외한다. {@code AdminAuthorizationService}가
     * {@code requireAccessible}로 정지 회원을 이미 차단하므로 이 정책의 대상이 아니다.
     * {@code /api/auth/**}도 {@code MemberAccessInterceptor} 제외 경로라 대상이 아니다.
     */
    private static List<Endpoint> writeEndpoints() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Endpoint> endpoints = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller = loadClass(definition.getBeanClassName());
            String prefix = classPrefix(controller);
            for (Method method : controller.getDeclaredMethods()) {
                for (var mapping : WRITE_MAPPINGS) {
                    var annotation = AnnotatedElementUtils.findMergedAnnotation(method, mapping);
                    if (annotation == null) continue;
                    String path = prefix + firstPath(annotation);
                    if (path.startsWith("/api/admin") || path.startsWith("/api/auth")) continue;
                    endpoints.add(new Endpoint(httpMethod(mapping), path));
                }
            }
        }
        // scanner나 경로 조립이 망가지면 빈 목록으로도 통과해 검사가 무의미해진다.
        // 2026-09-08 기준 관리자·auth 제외 상태 변경 endpoint는 23개다. 하한을 둬서 붕괴를 잡는다.
        assertThat(endpoints)
                .as("상태 변경 endpoint를 제대로 훑지 못했다. scanner나 경로 조립을 확인하라")
                .hasSizeGreaterThanOrEqualTo(20);
        return endpoints;
    }

    private static String classPrefix(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        if (mapping == null || mapping.value().length == 0) return "";
        return mapping.value()[0];
    }

    private static String firstPath(java.lang.annotation.Annotation annotation) {
        try {
            String[] value = (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
            return value.length == 0 ? "" : value[0];
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("mapping path를 읽지 못했습니다.", exception);
        }
    }

    private static String httpMethod(Class<? extends java.lang.annotation.Annotation> mapping) {
        if (mapping == PostMapping.class) return "POST";
        if (mapping == PutMapping.class) return "PUT";
        if (mapping == PatchMapping.class) return "PATCH";
        return "DELETE";
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("controller class를 불러오지 못했습니다: " + name, exception);
        }
    }

    /** {@code {festivalId}} 같은 template 변수를 실제 값으로 바꿔 pattern 매칭에 쓴다. */
    private record Endpoint(String method, String template) {
        String samplePath() {
            return template.replaceAll("\\{[^}]+}", "1");
        }
    }
}
