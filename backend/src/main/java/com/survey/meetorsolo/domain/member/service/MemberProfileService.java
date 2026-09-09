package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberProfileResponse;
import com.survey.meetorsolo.domain.member.dto.UpdateMemberProfileRequest;
import com.survey.meetorsolo.domain.member.dto.TravelStyleResponse;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import com.survey.meetorsolo.domain.member.entity.MemberTravelStyle;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import com.survey.meetorsolo.domain.member.repository.MemberConsentQueryRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.repository.MemberTravelStyleRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberProfileService {

    private final MemberRepository memberRepository;
    private final MemberTravelStyleRepository memberTravelStyleRepository;
    private final MemberConsentQueryRepository consentQueryRepository;
    private final ProfileFieldCrypto profileFieldCrypto;
    private final MemberAccessPolicy accessPolicy;

    public MemberProfileService(
            MemberRepository memberRepository,
            MemberTravelStyleRepository memberTravelStyleRepository,
            MemberConsentQueryRepository consentQueryRepository,
            ProfileFieldCrypto profileFieldCrypto,
            MemberAccessPolicy accessPolicy
    ) {
        this.memberRepository = memberRepository;
        this.memberTravelStyleRepository = memberTravelStyleRepository;
        this.consentQueryRepository = consentQueryRepository;
        this.profileFieldCrypto = profileFieldCrypto;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        Member member = findMember(memberId);
        return toResponse(member, findTravelStyles(memberId));
    }

    @Transactional
    public MemberProfileResponse completeProfile(Long memberId, UpdateMemberProfileRequest request) {
        Member member = findMember(memberId);
        // 정지 회원도 프로필을 수정할 수 있다(docs/19 4.8). 자기 정보 관리는 다른 사용자와의
        // 상호작용이 아니므로 제재 대상이 아니다. 영구 제한·탈퇴는 애초에 여기 도달하지 못한다.
        if (!Member.STATUS_PROFILE_REQUIRED.equals(member.getStatus())
                && !Member.STATUS_ACTIVE.equals(member.getStatus())
                && !Member.STATUS_SUSPENDED.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireSignupConsents(member);
        requireSelectableNickname(request.nickname().trim());

        member.completeProfile(
                request.nickname().trim(),
                normalize(request.email()),
                normalize(request.intro()),
                profileFieldCrypto.encrypt(request.gender()),
                profileFieldCrypto.encrypt(request.ageRange())
        );

        memberTravelStyleRepository.deleteAllByMemberId(memberId);
        var travelStyles = request.travelStyles().stream()
                .map(TravelStyleCode::valueOf)
                .map(styleCode -> MemberTravelStyle.of(member, styleCode))
                .toList();
        memberTravelStyleRepository.saveAll(travelStyles);

        return toResponse(member, travelStyles);
    }

    /**
     * 탈퇴 회원의 표시 문구는 닉네임으로 고를 수 없다.
     *
     * <p>허용하면 살아 있는 계정이 다른 사용자에게 탈퇴한 회원으로 보인다({@code docs/19} 4.4).
     *
     * <p><b>지금은 이 검사에 도달하기 전에 이미 막힌다.</b>
     * {@code UpdateMemberProfileRequest}의 {@code @Pattern(^[가-힣A-Za-z0-9]+$)}이 공백을
     * 허용하지 않아 "탈퇴한 회원"은 Bean Validation에서 {@code 400}으로 걸린다. 이 검사와
     * {@code V30}의 {@code chk_members_nickname_not_withdrawn_label}은 그 패턴이 완화되거나
     * 이 DTO를 거치지 않는 경로가 생겼을 때를 위한 이중 방어다.
     */
    private void requireSelectableNickname(String nickname) {
        if (Member.WITHDRAWN_NICKNAME.equals(nickname)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "사용할 수 없는 닉네임입니다.");
        }
    }

    /**
     * 최초 가입 완료 시점에 이용약관과 개인정보처리방침 동의를 요구한다.
     *
     * <p>기존 회원의 프로필 수정(`ACTIVE`)에는 적용하지 않는다. 동의 기록 구조가 생기기 전에
     * 가입한 회원까지 소급해 막으면 프로필 수정 자체가 불가능해지기 때문이다. 소급 동의 수집은
     * 별도 작업으로 다룬다.
     */
    private void requireSignupConsents(Member member) {
        if (!Member.STATUS_PROFILE_REQUIRED.equals(member.getStatus())) {
            return;
        }
        for (MemberConsentType type : List.of(MemberConsentType.TERMS, MemberConsentType.PRIVACY)) {
            if (!consentQueryRepository.hasAgreedConsent(member.getId(), type.name())) {
                throw new BusinessException(ErrorCode.SIGNUP_CONSENT_REQUIRED);
            }
        }
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private List<MemberTravelStyle> findTravelStyles(Long memberId) {
        return memberTravelStyleRepository.findAllByMemberIdOrderById(memberId);
    }

    private MemberProfileResponse toResponse(Member member, List<MemberTravelStyle> travelStyles) {
        return new MemberProfileResponse(
                member.getId(),
                member.getNickname(),
                member.getEmail(),
                member.getIntro(),
                resolveProfileImageUrl(member),
                profileFieldCrypto.decrypt(member.getGenderEncrypted()),
                profileFieldCrypto.decrypt(member.getAgeRangeEncrypted()),
                member.getStatus(),
                accessPolicy.sanctionNoticeOf(member),
                travelStyles.stream()
                        .map(MemberTravelStyle::getStyleCode)
                        .map(TravelStyleResponse::from)
                        .toList()
        );
    }

    private String resolveProfileImageUrl(Member member) {
        if (member.getProfileImageObjectKey() != null && !member.getProfileImageObjectKey().isBlank()) {
            return "/api/members/me/profile-image";
        }
        return normalize(member.getProfileImageUrl());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
