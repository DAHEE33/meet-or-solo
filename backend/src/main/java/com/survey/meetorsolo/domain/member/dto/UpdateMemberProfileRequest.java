package com.survey.meetorsolo.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

public record UpdateMemberProfileRequest(
        @NotBlank
        @Size(min = 2, max = 12)
        @Pattern(regexp = "^[가-힣A-Za-z0-9]+$")
        String nickname,

        @Email
        @Size(max = 255)
        String email,

        @Size(max = 160)
        String intro,

        @NotBlank
        @Pattern(regexp = "MALE|FEMALE|OTHER")
        String gender,

        @NotBlank
        @Pattern(regexp = "10S|20S|30S|40S|50S|60_PLUS")
        String ageRange,

        @NotNull
        @Size(min = 1, max = 3)
        @UniqueElements
        List<@NotBlank @Pattern(regexp = "RELAXED|ACTIVE|FOOD|PHOTO|CULTURE") String> travelStyles
) {
}
