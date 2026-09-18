package com.survey.meetorsolo.global.exception;

import com.survey.meetorsolo.domain.auth.service.SanctionNoticeCookieService;
import com.survey.meetorsolo.domain.member.service.MemberSanctionException;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.error.ErrorResponse;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SanctionNoticeCookieService sanctionNoticeCookies;

    public GlobalExceptionHandler(ObjectProvider<SanctionNoticeCookieService> sanctionNoticeCookies) {
        this.sanctionNoticeCookies = sanctionNoticeCookies.getIfAvailable();
    }

    /**
     * 제재로 막힌 요청은 사유·기간 안내를 body에 담고, 같은 응답에 단기 notice cookie를 싣는다.
     * cookie가 있으면 로그인 화면이 {@code GET /api/auth/sanction-notice}로 안내를 다시 읽을 수 있다.
     *
     * <p>{@code MemberSanctionException}은 {@code BusinessException}의 subclass이므로 이 handler가
     * 더 구체적인 type으로 먼저 선택된다.
     */
    @ExceptionHandler(MemberSanctionException.class)
    public ResponseEntity<ApiResponse<Void>> handleMemberSanctionException(MemberSanctionException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getStatus());
        if (sanctionNoticeCookies != null && exception.getMemberId() != null) {
            builder.header(
                    HttpHeaders.SET_COOKIE,
                    sanctionNoticeCookies.issue(exception.getMemberId()).toString());
        }
        return builder.body(ApiResponse.failure(
                ErrorResponse.ofSanction(errorCode, exception.getNotice())));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error(
                    "Business exception. code={}, exception={}, message={}",
                    errorCode.getCode(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );
        }
        ErrorResponse errorResponse = ErrorResponse.of(errorCode, exception.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorResponse));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        List<ErrorResponse.FieldError> fields = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(ErrorResponse.of(errorCode, fields)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        List<ErrorResponse.FieldError> fields = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ErrorResponse.FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(ErrorResponse.of(errorCode, fields)));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidInputException(Exception exception) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(ErrorResponse.of(errorCode)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception
    ) {
        ErrorCode errorCode = ErrorCode.PROFILE_IMAGE_TOO_LARGE;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.failure(ErrorResponse.of(errorCode)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        ErrorCode errorCode = ErrorCode.NOT_FOUND;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(ErrorResponse.of(errorCode)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(ErrorResponse.of(errorCode)));
    }

    private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
        return new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
