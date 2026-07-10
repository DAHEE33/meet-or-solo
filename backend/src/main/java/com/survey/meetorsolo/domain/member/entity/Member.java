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

    private Member(String provider, String providerUserId, String nickname, String profileImageUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public static Member createKakaoMember(String providerUserId, String nickname, String profileImageUrl) {
        Member member = new Member(PROVIDER_KAKAO, providerUserId, nickname, profileImageUrl);
        member.markLoggedIn();
        return member;
    }

    public void updateKakaoProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        markLoggedIn();
    }

    public void markLoggedIn() {
        this.lastLoginAt = SeoulDateTime.now();
    }

    public void completeProfile(String nickname, byte[] genderEncrypted, byte[] ageRangeEncrypted) {
        this.nickname = nickname;
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
}
