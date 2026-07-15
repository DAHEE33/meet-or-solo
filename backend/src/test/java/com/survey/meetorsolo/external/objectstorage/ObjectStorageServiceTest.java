package com.survey.meetorsolo.external.objectstorage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.survey.meetorsolo.global.config.ObjectStorageProperties;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.S3Client;

class ObjectStorageServiceTest {

    private final ObjectStorageService service = new ObjectStorageService(
            mock(S3Client.class),
            new ObjectStorageProperties(
                    "http://localhost:9000",
                    "us-ashburn-1",
                    "access-key",
                    "secret-key",
                    "meet-or-solo-assets",
                    "profiles/local",
                    DataSize.ofMegabytes(5)
            )
    );

    @Test
    void 허용하지_않는_MIME_타입은_거절한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.gif", "image/gif", new byte[]{0x47, 0x49, 0x46}
        );

        assertErrorCode(file, ErrorCode.INVALID_PROFILE_IMAGE);
    }

    @Test
    void MIME_타입과_파일_시그니처가_다르면_거절한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-a-png".getBytes()
        );

        assertErrorCode(file, ErrorCode.INVALID_PROFILE_IMAGE);
    }

    @Test
    void 최대_크기를_초과하면_거절한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]
        );

        assertErrorCode(file, ErrorCode.PROFILE_IMAGE_TOO_LARGE);
    }

    private void assertErrorCode(MockMultipartFile file, ErrorCode errorCode) {
        assertThatThrownBy(() -> service.uploadProfileImage(1L, file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(errorCode));
    }
}
