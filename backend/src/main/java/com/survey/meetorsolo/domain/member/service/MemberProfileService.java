package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberProfileResponse;
import com.survey.meetorsolo.domain.member.dto.UpdateMemberProfileRequest;
import com.survey.meetorsolo.domain.member.dto.TravelStyleResponse;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberTravelStyle;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
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
    private final ProfileFieldCrypto profileFieldCrypto;

    public MemberProfileService(
            MemberRepository memberRepository,
            MemberTravelStyleRepository memberTravelStyleRepository,
            ProfileFieldCrypto profileFieldCrypto
    ) {
        this.memberRepository = memberRepository;
        this.memberTravelStyleRepository = memberTravelStyleRepository;
        this.profileFieldCrypto = profileFieldCrypto;
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        Member member = findMember(memberId);
        return toResponse(member, findTravelStyles(memberId));
    }

    @Transactional
    public MemberProfileResponse completeProfile(Long memberId, UpdateMemberProfileRequest request) {
        Member member = findMember(memberId);
        if (!Member.STATUS_PROFILE_REQUIRED.equals(member.getStatus())
                && !Member.STATUS_ACTIVE.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        member.completeProfile(
                request.nickname().trim(),
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
                profileFieldCrypto.decrypt(member.getGenderEncrypted()),
                profileFieldCrypto.decrypt(member.getAgeRangeEncrypted()),
                member.getStatus(),
                travelStyles.stream()
                        .map(MemberTravelStyle::getStyleCode)
                        .map(TravelStyleResponse::from)
                        .toList()
        );
    }
}
