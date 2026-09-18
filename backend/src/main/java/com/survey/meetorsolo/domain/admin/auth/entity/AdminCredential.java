package com.survey.meetorsolo.domain.admin.auth.entity;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 슈퍼관리자 ID/PW 자격증명({@code docs/30}).
 *
 * <p>{@code members}와 1:1이다. 인증 수단만 담고 권한은 담지 않는다 — 관리자 여부는
 * 로그인할 때마다 {@code members.role}을 다시 본다.
 */
@Entity
@Table(name = "admin_credentials")
public class AdminCredential {

    /** 연속 실패 허용 횟수. 이 횟수에 도달하면 잠근다. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * 잠금 시간. 사람이 기다릴 수 있으면서 자동 대입에는 충분히 긴 값으로 고정한다.
     * 설정으로 빼지 않는 이유는 환경마다 다르게 둘 이유가 없기 때문이다.
     */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 50)
    private String username;

    /** BCrypt 해시. 평문은 어떤 경우에도 저장하지 않는다. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AdminCredential() {
    }

    private AdminCredential(Long memberId, String username, String passwordHash) {
        this.memberId = memberId;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public static AdminCredential issue(Long memberId, String username, String passwordHash) {
        return new AdminCredential(memberId, username, passwordHash);
    }

    /**
     * 비밀번호 회전. 잠금과 실패 누적도 함께 푼다.
     *
     * <p>비밀번호를 바꿨다는 것은 운영자가 개입했다는 뜻이다. 이전 비밀번호로 쌓인 실패
     * 횟수 때문에 새 비밀번호가 막히면 회전 자체가 복구 수단이 되지 못한다.
     */
    public void rotatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * 실패 1회 기록. 허용 횟수에 도달하면 잠근다.
     *
     * <p>잠금이 시작되면 카운터를 0으로 되돌린다. 되돌리지 않으면 잠금이 풀린 직후의
     * 첫 실패 한 번으로 다시 잠기고, 사실상 영구 잠금이 된다.
     */
    public void recordFailure(OffsetDateTime now) {
        this.failedAttempts += 1;
        if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.failedAttempts = 0;
            this.lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    public void recordSuccess(OffsetDateTime now) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = SeoulDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = SeoulDateTime.now();
    }
}
