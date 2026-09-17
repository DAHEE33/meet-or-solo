package com.survey.meetorsolo.domain.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Web Push(VAPID) 설정({@code docs/32} 3.4).
 *
 * <p><b>키는 환경변수로만 주입한다.</b> 저장소에 넣지 않는다 — 특히 private key가 유출되면
 * 우리 도메인 이름으로 아무나 push를 보낼 수 있다({@code docs/06} Secret 규칙).
 *
 * <p>키가 비어 있으면 발송을 끈다. local 개발에서 키를 만들지 않아도 나머지 기능이 그대로
 * 동작해야 하고, 켜고 끄는 별도 플래그를 두면 "키는 있는데 꺼져 있음" 같은 상태가 생긴다.
 */
@Component
public class WebPushProperties {

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushProperties(
            @Value("${app.push.vapid.public-key:}") String publicKey,
            @Value("${app.push.vapid.private-key:}") String privateKey,
            @Value("${app.push.vapid.subject:}") String subject
    ) {
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.subject = subject == null ? "" : subject.trim();
    }

    public boolean enabled() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    public String publicKey() {
        return publicKey;
    }

    public String privateKey() {
        return privateKey;
    }

    /** push 서비스가 요구하는 연락처(mailto: 또는 https:). 비어 있으면 mailto 기본값을 쓴다. */
    public String subject() {
        return subject.isBlank() ? "mailto:noreply@meet-or-solo.local" : subject;
    }
}
