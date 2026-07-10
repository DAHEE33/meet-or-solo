package com.survey.meetorsolo.domain.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateMemberProfileRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 여행_스타일이_null이면_검증에_실패한다() {
        var request = new UpdateMemberProfileRequest("닉네임", "FEMALE", "20S", null);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("travelStyles"));
    }

    @Test
    void 여행_스타일이_비어_있으면_검증에_실패한다() {
        var request = new UpdateMemberProfileRequest("닉네임", "FEMALE", "20S", List.of());

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("travelStyles"));
    }

    @Test
    void 여행_스타일이_세_개를_초과하면_검증에_실패한다() {
        var request = new UpdateMemberProfileRequest(
                "닉네임", "FEMALE", "20S", List.of("RELAXED", "ACTIVE", "FOOD", "PHOTO")
        );

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("travelStyles"));
    }

    @Test
    void 중복된_여행_스타일이면_검증에_실패한다() {
        var request = new UpdateMemberProfileRequest(
                "닉네임", "FEMALE", "20S", List.of("FOOD", "FOOD")
        );

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("travelStyles"));
    }

    @Test
    void 허용되지_않은_여행_스타일이면_검증에_실패한다() {
        var request = new UpdateMemberProfileRequest(
                "닉네임", "FEMALE", "20S", List.of("UNKNOWN")
        );

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().startsWith("travelStyles"));
    }

    @Test
    void 허용된_여행_스타일_한_개에서_세_개는_검증에_성공한다() {
        var request = new UpdateMemberProfileRequest(
                "닉네임", "FEMALE", "20S", List.of("RELAXED", "FOOD", "PHOTO")
        );

        assertThat(validator.validate(request)).isEmpty();
    }
}
