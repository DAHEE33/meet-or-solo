package com.survey.meetorsolo.domain.member.entity;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "member_travel_styles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_member_travel_styles_member_style",
                        columnNames = {"member_id", "style_code"}
                )
        }
)
public class MemberTravelStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "style_code", nullable = false, length = 30)
    private TravelStyleCode styleCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MemberTravelStyle() {
    }

    private MemberTravelStyle(Member member, TravelStyleCode styleCode) {
        this.member = member;
        this.styleCode = styleCode;
    }

    public static MemberTravelStyle of(Member member, TravelStyleCode styleCode) {
        return new MemberTravelStyle(member, styleCode);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = SeoulDateTime.now();
    }

    public TravelStyleCode getStyleCode() {
        return styleCode;
    }
}
