package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.member.dto.MemberProfileResponse;
import com.survey.meetorsolo.domain.member.dto.UpdateMemberProfileRequest;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberTravelStyle;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.repository.MemberTravelStyleRepository;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberTravelStyleRepository memberTravelStyleRepository;

    @Test
    void 프로필을_저장하면_ACTIVE로_전환한다() {
        ProfileFieldCrypto crypto = new ProfileFieldCrypto(
                Base64.getEncoder().encodeToString(new byte[32])
        );
        Member member = Member.createKakaoMember("provider-user-id", "기존닉네임", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        MemberProfileService service = new MemberProfileService(
                memberRepository,
                memberTravelStyleRepository,
                crypto
        );

        MemberProfileResponse response = service.completeProfile(
                1L,
                new UpdateMemberProfileRequest("새닉네임", "FEMALE", "20S", List.of("FOOD", "PHOTO"))
        );

        assertThat(response.status()).isEqualTo(Member.STATUS_ACTIVE);
        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.gender()).isEqualTo("FEMALE");
        assertThat(response.ageRange()).isEqualTo("20S");
        assertThat(response.travelStyles())
                .extracting("code")
                .containsExactly("FOOD", "PHOTO");
        assertThat(member.getGenderEncrypted()).isNotEqualTo("FEMALE".getBytes());

        ArgumentCaptor<List<MemberTravelStyle>> captor = ArgumentCaptor.forClass(List.class);
        verify(memberTravelStyleRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(MemberTravelStyle::getStyleCode)
                .containsExactly(
                        TravelStyleCode.FOOD,
                        TravelStyleCode.PHOTO
                );
    }

    @Test
    void 프로필을_재저장하면_기존_여행_스타일을_삭제한_뒤_새_값을_저장한다() {
        ProfileFieldCrypto crypto = crypto();
        Member member = Member.createKakaoMember("provider-user-id", "기존닉네임", null);
        member.completeProfile("기존닉네임", crypto.encrypt("MALE"), crypto.encrypt("30S"));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        MemberProfileService service = service(crypto);

        service.completeProfile(
                1L,
                new UpdateMemberProfileRequest("새닉네임", "FEMALE", "20S", List.of("CULTURE"))
        );

        InOrder inOrder = inOrder(memberTravelStyleRepository);
        inOrder.verify(memberTravelStyleRepository).deleteAllByMemberId(1L);
        inOrder.verify(memberTravelStyleRepository).saveAll(anyList());
    }

    @Test
    void 여행_스타일_저장에_실패하면_예외가_트랜잭션_밖으로_전파된다() {
        ProfileFieldCrypto crypto = crypto();
        Member member = Member.createKakaoMember("provider-user-id", "기존닉네임", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberTravelStyleRepository.saveAll(anyList()))
                .thenThrow(new IllegalStateException("style persistence failed"));
        MemberProfileService service = service(crypto);

        assertThatThrownBy(() -> service.completeProfile(
                1L,
                new UpdateMemberProfileRequest("새닉네임", "FEMALE", "20S", List.of("FOOD"))
        )).isInstanceOf(IllegalStateException.class);
    }

    private ProfileFieldCrypto crypto() {
        return new ProfileFieldCrypto(Base64.getEncoder().encodeToString(new byte[32]));
    }

    private MemberProfileService service(ProfileFieldCrypto crypto) {
        return new MemberProfileService(memberRepository, memberTravelStyleRepository, crypto);
    }
}
