package com.survey.meetorsolo.domain.member.entity;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_members_provider_user", columnNames = {"provider", "provider_user_id"})
        }
)
public class Member {

    public static final String PROVIDER_KAKAO = "KAKAO";
    public static final String PROVIDER_NAVER = "NAVER";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String STATUS_PROFILE_REQUIRED = "PROFILE_REQUIRED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_BANNED = "BANNED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
    public static final String STATUS_DELETED = "DELETED";

    /**
     * 탈퇴 회원의 표시용 닉네임. <b>컬럼에 저장하는 값이 아니다.</b>
     *
     * <p>{@code withdraw()}는 닉네임을 {@code NULL}로 지우고, 이 문구는 조회 SQL이
     * {@code status = 'WITHDRAWN'}일 때 만들어 낸다({@code V30}의 CHECK가 컬럼에 저장되는
     * 것을 거부한다). 문구를 컬럼에 넣으면 재가입한 계정이 그 값을 그대로 들고 살아나고,
     * 문구가 곧 "익명화됐는지"를 뜻하는 상태 flag가 되어 문구를 바꾸는 순간 기존 행이
     * 판정에서 빠진다.
     *
     * <p>이 상수가 쓰이는 곳은 두 군데다 — 조회 SQL 리터럴과의 일치 검증, 그리고 살아 있는
     * 회원이 이 문구를 닉네임으로 고르지 못하게 막는 {@code MemberProfileService}다.
     */
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 120)
    private String providerUserId;

    @Column(length = 50)
    private String nickname;

    @Column(length = 255)
    private String email;

    @Column(length = 160)
    private String intro;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Column(name = "profile_image_object_key", length = 1000)
    private String profileImageObjectKey;

    @Column(name = "gender_encrypted")
    private byte[] genderEncrypted;

    @Column(name = "age_range_encrypted")
    private byte[] ageRangeEncrypted;

    @Column(name = "manner_temperature", nullable = false, precision = 5, scale = 2)
    private BigDecimal mannerTemperature = new BigDecimal("36.50");

    @Column(name = "penalty_score", nullable = false)
    private Integer penaltyScore = 0;

    @Column(nullable = false, length = 30)
    private String role = ROLE_USER;

    @Column(nullable = false, length = 30)
    private String status = STATUS_PROFILE_REQUIRED;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "suspended_until")
    private OffsetDateTime suspendedUntil;

    @Column(name = "status_before_sanction", length = 30)
    private String statusBeforeSanction;

    /**
     * 사용자에게 노출할 제재 사유 code. 제재 상태에서만 값이 있다.
     * 관리자 내부용 자유 입력 note({@code admin_actions.reason})와 분리된 값이므로
     * 신고자 수·시점을 추정할 수 있는 정보는 여기에 담기지 않는다.
     */
    @Column(name = "sanction_reason_code", length = 40)
    private String sanctionReasonCode;

    /**
     * 탈퇴 시점의 상태. {@code WITHDRAWN}일 때만 값이 있다.
     *
     * <p>아래 스냅샷 컬럼들은 제재 컬럼({@code suspended_until},
     * {@code sanction_reason_code})을 재사용하지 않는다. {@code V19}의
     * {@code chk_members_suspension_period}와 {@code V27}의
     * {@code chk_members_sanction_reason_presence}가 제재 상태가 아닌 회원에게
     * 그 값이 남는 것을 금지하기 때문이다. 자세한 이유는 {@code V28} 주석에 있다.
     */
    @Column(name = "withdrawn_from_status", length = 30)
    private String withdrawnFromStatus;

    /** 관리자 강제 탈퇴인지. 정책이 아니라 사실을 기록한다. */
    @Column(name = "withdrawn_by_admin")
    private Boolean withdrawnByAdmin;

    /** 재가입 영구 거부 여부. 로그인 경로가 읽는 유일한 값이다. */
    @Column(name = "withdrawn_rejoin_blocked")
    private Boolean withdrawnRejoinBlocked;

    /** 탈퇴 시점의 잔여 정지 종료 시각. 재가입 시 정지를 이어받는 데 쓴다. */
    @Column(name = "withdrawn_suspended_until")
    private OffsetDateTime withdrawnSuspendedUntil;

    /** 탈퇴 시점의 사용자 노출용 제재 사유 code. 재가입 시 정지 복원에 필요하다. */
    @Column(name = "withdrawn_sanction_reason_code", length = 40)
    private String withdrawnSanctionReasonCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Member() {
    }

    private Member(String provider, String providerUserId, String email, String nickname, String profileImageUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public static Member createKakaoMember(String providerUserId, String nickname, String profileImageUrl) {
        return createKakaoMember(providerUserId, null, nickname, profileImageUrl);
    }

    public static Member createKakaoMember(
            String providerUserId, String email, String nickname, String profileImageUrl) {
        return createSocialMember(PROVIDER_KAKAO, providerUserId, email, nickname, profileImageUrl);
    }

    public static Member createNaverMember(String providerUserId, String nickname, String profileImageUrl) {
        return createNaverMember(providerUserId, null, nickname, profileImageUrl);
    }

    public static Member createNaverMember(
            String providerUserId, String email, String nickname, String profileImageUrl) {
        return createSocialMember(PROVIDER_NAVER, providerUserId, email, nickname, profileImageUrl);
    }

    private static Member createSocialMember(
            String provider,
            String providerUserId,
            String email,
            String nickname,
            String profileImageUrl
    ) {
        Member member = new Member(provider, providerUserId, email, nickname, profileImageUrl);
        member.markLoggedIn();
        return member;
    }

    public void updateKakaoProfile(String nickname, String profileImageUrl) {
        updateKakaoProfile(null, nickname, profileImageUrl);
    }

    public void updateKakaoProfile(String email, String nickname, String profileImageUrl) {
        updateSocialProfile(email, nickname, profileImageUrl);
    }

    public void updateNaverProfile(String nickname, String profileImageUrl) {
        updateNaverProfile(null, nickname, profileImageUrl);
    }

    public void updateNaverProfile(String email, String nickname, String profileImageUrl) {
        updateSocialProfile(email, nickname, profileImageUrl);
    }

    private void updateSocialProfile(String email, String nickname, String profileImageUrl) {
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
        // 평소에는 사용자가 직접 고친 닉네임을 OAuth 값으로 덮지 않는다.
        //
        // 예외는 닉네임이 비어 있는 경우다. 탈퇴가 닉네임을 NULL로 지우므로(withdraw) 재가입한
        // 계정은 여기서 채워지지 않으면 닉네임 없이 살아난다. 정지 중 탈퇴한 회원은 재가입하면
        // PROFILE_REQUIRED가 아니라 SUSPENDED로 부활하므로(잔여 정지 이어받기, docs/19 4.4)
        // status 조건만으로는 걸리지 않는다.
        //
        // 익명화 문구를 sentinel로 쓰지 않는다. 표시 문구를 상태 판정에 쓰면 문구를 바꾸는 순간
        // 기존 행이 판정에서 빠지고, OAuth가 닉네임을 주지 않으면(카카오는 닉네임 제공이 선택
        // 동의다) 문구가 살아 있는 계정에 그대로 남는다.
        boolean nicknameMissing = this.nickname == null || this.nickname.isBlank();
        if ((STATUS_PROFILE_REQUIRED.equals(status) || nicknameMissing)
                && nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null && !profileImageUrl.isBlank()) {
            this.profileImageUrl = profileImageUrl;
        }
        markLoggedIn();
    }

    public void markLoggedIn() {
        this.lastLoginAt = SeoulDateTime.now();
    }

    /**
     * 프로필 최초 입력과 이후 수정에 모두 쓰인다.
     *
     * <p><b>status를 무조건 {@code ACTIVE}로 덮으면 안 된다.</b> 정지 회원도 프로필을 수정할 수
     * 있으므로(docs/19 4.8), 덮어쓰면 제재가 조용히 풀리고 {@code suspended_until}과 사유는
     * 남아 {@code chk_members_suspension_period}·{@code chk_members_sanction_reason_presence}
     * 위반으로 저장 자체가 실패한다. 승격은 {@code PROFILE_REQUIRED}에서만 한다.
     */
    public void completeProfile(
            String nickname,
            String email,
            String intro,
            byte[] genderEncrypted,
            byte[] ageRangeEncrypted
    ) {
        this.nickname = nickname;
        this.email = email;
        this.intro = intro;
        this.genderEncrypted = genderEncrypted;
        this.ageRangeEncrypted = ageRangeEncrypted;
        if (STATUS_PROFILE_REQUIRED.equals(status)) {
            this.status = STATUS_ACTIVE;
        } else if (STATUS_PROFILE_REQUIRED.equals(statusBeforeSanction)) {
            // 정지 중에 프로필을 완성했으면 해제 후 돌아갈 상태도 ACTIVE여야 한다.
            // 그대로 두면 정지가 풀린 뒤 다시 가입 화면으로 보내진다.
            this.statusBeforeSanction = STATUS_ACTIVE;
        }
    }

    public void suspend(OffsetDateTime suspendedAt, OffsetDateTime suspendedUntil, String sanctionReasonCode) {
        requireSanctionableStatus();
        if (suspendedAt == null || suspendedUntil == null || !suspendedUntil.isAfter(suspendedAt)) {
            throw new IllegalArgumentException("정지 종료 시각은 시작 시각보다 이후여야 합니다.");
        }
        requireSanctionReasonCode(sanctionReasonCode);
        this.statusBeforeSanction = this.status;
        this.status = STATUS_SUSPENDED;
        this.suspendedAt = suspendedAt;
        this.suspendedUntil = suspendedUntil;
        this.sanctionReasonCode = sanctionReasonCode;
    }

    public void ban(String sanctionReasonCode) {
        // 상태를 바꾸기 전에 검증을 끝낸다. 뒤에서 던지면 statusBeforeSanction만 바뀐 채로 남는다.
        requireSanctionReasonCode(sanctionReasonCode);
        if (STATUS_ACTIVE.equals(status) || STATUS_PROFILE_REQUIRED.equals(status)) {
            this.statusBeforeSanction = status;
        } else if (!STATUS_SUSPENDED.equals(status)) {
            throw new IllegalStateException("현재 회원 상태에서는 영구차단할 수 없습니다.");
        }
        this.status = STATUS_BANNED;
        this.suspendedAt = null;
        this.suspendedUntil = null;
        this.sanctionReasonCode = sanctionReasonCode;
    }

    public void unban() {
        if (!STATUS_BANNED.equals(status) || !isRestorableStatus(statusBeforeSanction)) {
            throw new IllegalStateException("현재 회원 상태에서는 영구차단을 해제할 수 없습니다.");
        }
        restorePreviousStatus();
    }

    public void unsuspend() {
        if (!STATUS_SUSPENDED.equals(status) || !isRestorableStatus(statusBeforeSanction)) {
            throw new IllegalStateException("현재 회원 상태에서는 정지를 해제할 수 없습니다.");
        }
        restorePreviousStatus();
    }

    /**
     * 탈퇴 처리. 개인정보를 익명화하고 제재 상태를 스냅샷으로 옮긴다.
     *
     * <p>물리 삭제는 하지 않는다. {@code members}를 참조하는 FK 31개가 전부
     * {@code ON DELETE RESTRICT}이고, 신고와 제재 감사 이력이 탈퇴 회원을 참조한다.
     *
     * @param byAdmin     관리자 강제 탈퇴 여부
     * @param blockRejoin 재가입을 영구 거부할지. 본인 탈퇴는 항상 {@code false}
     */
    public void withdraw(OffsetDateTime now, boolean byAdmin, boolean blockRejoin) {
        // 상태를 바꾸기 전에 검증을 끝낸다. 뒤에서 던지면 엔티티가 반쯤 바뀐 채로 남는다.
        if (now == null) {
            throw new IllegalArgumentException("탈퇴 시각이 필요합니다.");
        }
        if (STATUS_WITHDRAWN.equals(status) || STATUS_DELETED.equals(status)) {
            throw new IllegalStateException("이미 탈퇴한 회원입니다.");
        }
        if (!byAdmin && STATUS_BANNED.equals(status)) {
            // 영구차단 회원은 로그인과 /api/** 요청이 모두 막혀 이 경로에 도달할 수 없다.
            // 도달했다면 접근 판정이 뚫린 것이므로 여기서 막는다.
            throw new IllegalStateException("영구차단 회원은 본인 탈퇴 경로를 쓸 수 없습니다.");
        }
        if (!byAdmin && blockRejoin) {
            throw new IllegalArgumentException("본인 탈퇴는 재가입을 차단하지 않습니다.");
        }

        this.withdrawnFromStatus = status;
        if (STATUS_SUSPENDED.equals(status) && suspendedUntil != null && suspendedUntil.isAfter(now)) {
            // 잔여 정지 기간을 남긴다. 이걸 버리면 정지 회원이 탈퇴 후 재가입으로 제재를 씻는다.
            this.withdrawnSuspendedUntil = suspendedUntil;
            this.withdrawnSanctionReasonCode = sanctionReasonCode;
        } else if (STATUS_BANNED.equals(status)) {
            this.withdrawnSanctionReasonCode = sanctionReasonCode;
        }
        this.withdrawnByAdmin = byAdmin;
        this.withdrawnRejoinBlocked = blockRejoin;
        this.withdrawnAt = now;
        this.status = STATUS_WITHDRAWN;

        // 제재 컬럼은 비운다. WITHDRAWN에 남으면 V19·V27 제약이 저장을 거부한다.
        this.statusBeforeSanction = null;
        this.suspendedAt = null;
        this.suspendedUntil = null;
        this.sanctionReasonCode = null;

        // 닉네임은 문구로 덮지 않고 지운다. '탈퇴한 회원' 표시는 조회 SQL이 status로 만든다.
        // 컬럼에 문구를 넣으면 재가입한 계정이 그 값을 들고 살아나고 문구가 상태 flag가 된다.
        this.nickname = null;
        this.email = null;
        this.intro = null;
        this.profileImageUrl = null;
        this.profileImageObjectKey = null;
        this.genderEncrypted = null;
        this.ageRangeEncrypted = null;
    }

    /**
     * 탈퇴 후 재가입. 스냅샷을 비우고 프로필 재입력 상태로 되살린다.
     *
     * <p>쿨오프 경과와 재가입 차단 판정은 호출부({@code MemberRejoinPolicy})가 끝낸 뒤에
     * 호출한다. 잔여 정지 기간이 남아 있으면 정지를 이어받는다.
     */
    public void rejoin(OffsetDateTime now) {
        if (!STATUS_WITHDRAWN.equals(status)) {
            throw new IllegalStateException("탈퇴 상태의 회원만 재가입할 수 있습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("재가입 시각이 필요합니다.");
        }
        boolean suspensionRemains =
                withdrawnSuspendedUntil != null && withdrawnSuspendedUntil.isAfter(now);
        OffsetDateTime remainingUntil = withdrawnSuspendedUntil;
        String remainingReasonCode = withdrawnSanctionReasonCode;

        this.withdrawnAt = null;
        this.withdrawnFromStatus = null;
        this.withdrawnByAdmin = null;
        this.withdrawnRejoinBlocked = null;
        this.withdrawnSuspendedUntil = null;
        this.withdrawnSanctionReasonCode = null;

        if (suspensionRemains) {
            // 프로필이 익명화되어 비어 있으므로 정지 해제 후 돌아갈 상태는 PROFILE_REQUIRED다.
            // completeProfile이 정지 중 프로필 완성 시 이 값을 ACTIVE로 올려준다.
            this.statusBeforeSanction = STATUS_PROFILE_REQUIRED;
            this.status = STATUS_SUSPENDED;
            this.suspendedAt = now;
            this.suspendedUntil = remainingUntil;
            this.sanctionReasonCode = remainingReasonCode;
        } else {
            this.status = STATUS_PROFILE_REQUIRED;
        }
    }

    /**
     * 관리자 유효 판정 신고의 penalty score를 누적한다.
     * {@code penalty_score}는 감사 목적의 누적값이므로 상한을 두지 않는다.
     */
    public void increasePenaltyScore(int scoreDelta) {
        if (scoreDelta <= 0) {
            throw new IllegalArgumentException("penalty score 증가량은 양수여야 합니다.");
        }
        this.penaltyScore = this.penaltyScore + scoreDelta;
    }

    /**
     * 매너온도를 하한까지만 차감하고 실제로 적용된 차감량을 반환한다.
     * 이미 하한이면 {@code 0.00}을 반환한다. 후기 기능이 없어 상승 경로가 없으므로
     * 하한 clamp로 무한 하강을 막는다.
     */
    public BigDecimal decreaseMannerTemperature(BigDecimal amount, BigDecimal floor) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("매너온도 차감량은 양수여야 합니다.");
        }
        if (floor == null || floor.signum() < 0) {
            throw new IllegalArgumentException("매너온도 하한은 0 이상이어야 합니다.");
        }
        BigDecimal current = this.mannerTemperature;
        if (current.compareTo(floor) <= 0) {
            return BigDecimal.ZERO.setScale(current.scale());
        }
        BigDecimal target = current.subtract(amount).max(floor);
        this.mannerTemperature = target;
        return current.subtract(target);
    }

    /**
     * 매너온도를 상한까지만 올리고 실제로 적용된 상승량을 반환한다({@code docs/19} 4.9).
     *
     * <p>이미 상한이면 {@code 0.00}을 반환한다. {@link #decreaseMannerTemperature}와 대칭이며
     * 호출자는 반환값이 0이면 이력을 남기지 않는다 — 아무 일도 일어나지 않은 사건을 기록하면
     * "완료 보상을 몇 번 받았나"를 셀 수 없다.
     *
     * <p>상한을 인자로 받는 이유는 <b>경로마다 상한이 다르기 때문</b>이다. 시간 경과 회복은
     * 시작값까지만 올리고, 만남 완료 보상은 상한까지 올린다
     * ({@code MannerTemperaturePolicy.timeRecoveryCeiling}).
     */
    public BigDecimal increaseMannerTemperature(BigDecimal amount, BigDecimal ceiling) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("매너온도 상승량은 양수여야 합니다.");
        }
        if (ceiling == null) {
            throw new IllegalArgumentException("매너온도 상한이 필요합니다.");
        }
        BigDecimal current = this.mannerTemperature;
        if (current.compareTo(ceiling) >= 0) {
            return BigDecimal.ZERO.setScale(current.scale());
        }
        BigDecimal target = current.add(amount).min(ceiling);
        this.mannerTemperature = target;
        return target.subtract(current);
    }

    /**
     * 매너온도를 지정한 값으로 바꾸고 변경 전 값을 반환한다(관리자 수동 조정, {@code docs/19} 4.9).
     *
     * <p>{@link #decreaseMannerTemperature}와 달리 <b>차감량이 아니라 목표값을 받는다.</b>
     * 관리자는 "36.5로 되돌린다"를 직관적으로 다루고, 감사 로그에는 어차피 변경 전후 값을
     * 함께 남기므로 delta 방식과 추적력이 같다.
     *
     * <p>범위를 벗어난 값은 clamp하지 않고 거절한다. clamp하면 관리자가 입력한 값과 실제
     * 저장된 값이 조용히 달라져, 감사 로그를 읽는 사람이 관리자의 의도를 알 수 없다.
     */
    public BigDecimal adjustMannerTemperature(BigDecimal target, BigDecimal floor, BigDecimal ceiling) {
        if (target == null) {
            throw new IllegalArgumentException("매너온도 목표값이 필요합니다.");
        }
        if (floor == null || ceiling == null || floor.compareTo(ceiling) > 0) {
            throw new IllegalArgumentException("매너온도 허용 범위가 올바르지 않습니다.");
        }
        if (target.compareTo(floor) < 0 || target.compareTo(ceiling) > 0) {
            throw new IllegalArgumentException("매너온도는 허용 범위 안의 값이어야 합니다.");
        }
        BigDecimal before = this.mannerTemperature;
        this.mannerTemperature = target.setScale(before.scale(), java.math.RoundingMode.HALF_UP);
        return before;
    }

    public boolean restoreExpiredSuspension(OffsetDateTime now) {
        if (!STATUS_SUSPENDED.equals(status) || suspendedUntil == null || suspendedUntil.isAfter(now)) {
            return false;
        }
        restorePreviousStatus();
        return true;
    }

    public boolean isAccessAllowed(OffsetDateTime now) {
        restoreExpiredSuspension(now);
        return STATUS_ACTIVE.equals(status) || STATUS_PROFILE_REQUIRED.equals(status);
    }

    private void requireSanctionableStatus() {
        if (!STATUS_ACTIVE.equals(status) && !STATUS_PROFILE_REQUIRED.equals(status)) {
            throw new IllegalStateException("현재 회원 상태에서는 정지할 수 없습니다.");
        }
    }

    private static void requireSanctionReasonCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("제재에는 사용자 노출용 사유 code가 필요합니다.");
        }
    }

    private void restorePreviousStatus() {
        String restored = statusBeforeSanction;
        if (!isRestorableStatus(restored)) {
            throw new IllegalStateException("복구할 회원 상태가 올바르지 않습니다.");
        }
        this.status = restored;
        this.statusBeforeSanction = null;
        this.suspendedAt = null;
        this.suspendedUntil = null;
        this.sanctionReasonCode = null;
    }

    private static boolean isRestorableStatus(String value) {
        return STATUS_ACTIVE.equals(value) || STATUS_PROFILE_REQUIRED.equals(value);
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = SeoulDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = SeoulDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getIntro() {
        return intro;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getProfileImageObjectKey() {
        return profileImageObjectKey;
    }

    public void updateProfileImageObjectKey(String profileImageObjectKey) {
        this.profileImageObjectKey = profileImageObjectKey;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public Integer getPenaltyScore() {
        return penaltyScore;
    }

    public BigDecimal getMannerTemperature() {
        return mannerTemperature;
    }

    public OffsetDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public OffsetDateTime getSuspendedUntil() {
        return suspendedUntil;
    }

    public String getStatusBeforeSanction() {
        return statusBeforeSanction;
    }

    public String getSanctionReasonCode() {
        return sanctionReasonCode;
    }

    public byte[] getGenderEncrypted() {
        return genderEncrypted;
    }

    public byte[] getAgeRangeEncrypted() {
        return ageRangeEncrypted;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public OffsetDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    public String getWithdrawnFromStatus() {
        return withdrawnFromStatus;
    }

    public boolean isWithdrawnByAdmin() {
        return Boolean.TRUE.equals(withdrawnByAdmin);
    }

    public boolean isRejoinBlocked() {
        return Boolean.TRUE.equals(withdrawnRejoinBlocked);
    }

    public OffsetDateTime getWithdrawnSuspendedUntil() {
        return withdrawnSuspendedUntil;
    }

    public String getWithdrawnSanctionReasonCode() {
        return withdrawnSanctionReasonCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
