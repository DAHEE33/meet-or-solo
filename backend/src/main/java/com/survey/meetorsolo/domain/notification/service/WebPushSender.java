package com.survey.meetorsolo.domain.notification.service;

import com.survey.meetorsolo.domain.notification.config.WebPushProperties;
import jakarta.annotation.PreDestroy;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Web Push 한 건을 실제로 보낸다({@code docs/32} 3.4).
 *
 * <p><b>요청 thread에서 보내지 않는다.</b> push 서비스(FCM·Mozilla·Apple)는 우리가 통제할 수
 * 없는 외부 HTTP 호출이고, 한 사람이 기기 여러 대를 구독할 수 있다. 도착·취소 같은 이벤트는
 * 사용자 요청 thread의 {@code AFTER_COMMIT}에서 발행되므로, 여기서 기다리면 응답이 그만큼
 * 늦어진다. 그래서 작은 전용 thread pool에 넘기고 즉시 돌아온다.
 *
 * <p>키가 없으면 아무것도 하지 않는다. local 개발에서 VAPID 키를 만들지 않아도 나머지 알림
 * 경로(WebSocket·알림함)가 그대로 동작해야 한다.
 */
@Component
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    /** 구독이 사라졌음을 뜻하는 상태 코드. 이때는 구독 행을 지운다. */
    private static final int NOT_FOUND = 404;
    private static final int GONE = 410;

    private final PushService pushService;
    private final ExecutorService workers;

    public WebPushSender(WebPushProperties properties) {
        this.pushService = createPushService(properties);
        this.workers = pushService == null
                ? null
                : Executors.newFixedThreadPool(2, runnable -> {
                    Thread thread = new Thread(runnable, "web-push");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static PushService createPushService(WebPushProperties properties) {
        if (!properties.enabled()) {
            log.info("VAPID 키가 없어 Web Push 발송을 비활성화합니다.");
            return null;
        }
        // 라이브러리가 EC 키를 "BC" provider로 읽는다. 등록하지 않으면 구독 키 파싱부터 실패한다.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            return new PushService(
                    properties.publicKey(), properties.privateKey(), properties.subject());
        } catch (Exception failure) {
            // 키 형식이 잘못된 경우다. 기동을 막지 않는다 — push가 없다고 매칭이 멈추면 안 된다.
            log.error("VAPID 키를 읽지 못해 Web Push 발송을 비활성화합니다.", failure);
            return null;
        }
    }

    public boolean enabled() {
        return pushService != null;
    }

    /**
     * 구독 한 건에 보낸다. 구독이 사라졌으면 {@code onGone}을 부른다.
     *
     * <p>발송 실패를 던지지 않는다. 호출자는 이미 WebSocket과 알림함으로 같은 내용을 전달한
     * 뒤이고, push는 그 위에 얹는 경로다.
     */
    public void sendAsync(
            String endpoint, String p256dh, String auth, String payload, Runnable onGone) {
        if (!enabled()) return;
        workers.execute(() -> send(endpoint, p256dh, auth, payload, onGone));
    }

    private void send(String endpoint, String p256dh, String auth, String payload, Runnable onGone) {
        try {
            int status = pushService
                    .send(new Notification(endpoint, p256dh, auth, payload))
                    .getStatusLine()
                    .getStatusCode();
            if (status == NOT_FOUND || status == GONE) {
                // 브라우저가 구독을 버렸다(앱 삭제·권한 회수·오래된 구독). 계속 들고 있으면
                // 매번 실패하므로 지운다.
                onGone.run();
                return;
            }
            if (status >= 400) {
                log.warn("Web Push 발송이 거절됐습니다. status={}", status);
            }
        } catch (Exception failure) {
            log.warn("Web Push 발송에 실패했습니다.", failure);
        }
    }

    @PreDestroy
    void shutdown() {
        if (workers == null) return;
        workers.shutdown();
        try {
            if (!workers.awaitTermination(3, TimeUnit.SECONDS)) workers.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }
}
