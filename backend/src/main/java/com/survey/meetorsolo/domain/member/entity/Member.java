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
    public static final String STATUS_PROFILE_REQUIRED = "PROFILE_REQUIRED";
    public static final String STATUS_ACTIVE = "ACTIVE";

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
        if (STATUS_PROFILE_REQUIRED.equals(status) && nickname != null && !nickname.isBlank()) {
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
        this.status = STATUS_ACTIVE;
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

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
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
}
