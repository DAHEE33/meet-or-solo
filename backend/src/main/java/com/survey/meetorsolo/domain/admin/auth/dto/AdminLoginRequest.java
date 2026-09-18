package com.survey.meetorsolo.domain.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자 ID/PW 로그인 요청({@code docs/30}).
 *
 * <p>길이 상한만 둔다. 형식 검증을 여기서 더 하면 "이 아이디는 형식이 틀렸다"와 "아이디가
 * 없다"가 다른 응답으로 갈라져 아이디를 열거할 단서가 된다.
 */
public record AdminLoginRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 200) String password
) {
}
