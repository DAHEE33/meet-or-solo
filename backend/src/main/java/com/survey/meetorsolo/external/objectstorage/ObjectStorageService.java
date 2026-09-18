package com.survey.meetorsolo.external.objectstorage;

import com.survey.meetorsolo.global.config.ObjectStorageProperties;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class ObjectStorageService {

    private static final Map<String, ImageType> IMAGE_TYPES = Map.of(
            "image/jpeg", new ImageType("jpg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}),
            "image/png", new ImageType("png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
            "image/webp", new ImageType("webp", new byte[]{0x52, 0x49, 0x46, 0x46})
    );

    private final S3Client s3Client;
    private final ObjectStorageProperties properties;

    public ObjectStorageService(S3Client s3Client, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public String uploadProfileImage(Long memberId, MultipartFile file) {
        byte[] content = validateAndRead(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        ImageType imageType = IMAGE_TYPES.get(contentType);
        String objectKey = normalizedPrefix() + "/" + memberId + "/" + UUID.randomUUID() + "." + imageType.extension();
        try {
            s3Client.putObject(builder -> builder.bucket(properties.bucket()).key(objectKey).contentType(contentType),
                    RequestBody.fromBytes(content));
            return objectKey;
        } catch (S3Exception exception) {
            throw new BusinessException(ErrorCode.OBJECT_STORAGE_ERROR, exception);
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.OBJECT_STORAGE_ERROR, exception);
        }
    }

    public StoredObject download(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    builder -> builder.bucket(properties.bucket()).key(objectKey));
            String contentType = response.response().contentType();
            return new StoredObject(response.asByteArray(), contentType == null ? "application/octet-stream" : contentType);
        } catch (NoSuchKeyException exception) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_FOUND);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_FOUND);
            }
            throw new BusinessException(ErrorCode.OBJECT_STORAGE_ERROR, exception);
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.OBJECT_STORAGE_ERROR, exception);
        }
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        try {
            s3Client.deleteObject(builder -> builder.bucket(properties.bucket()).key(objectKey));
        } catch (S3Exception ignored) {
            // 새 이미지 업로드와 DB 갱신 성공을 이전 이미지 정리 실패로 되돌리지 않는다.
        } catch (SdkException ignored) {
            // 저장소 연결 장애도 사용자 프로필 갱신 결과에는 영향을 주지 않는다.
        }
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getContentType() == null) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE);
        }
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        ImageType imageType = IMAGE_TYPES.get(contentType);
        if (imageType == null || file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException(file.getSize() > properties.maxFileSize().toBytes()
                    ? ErrorCode.PROFILE_IMAGE_TOO_LARGE : ErrorCode.INVALID_PROFILE_IMAGE);
        }
        try {
            byte[] content = file.getBytes();
            if (!matchesSignature(contentType, content, imageType.signature())) {
                throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE);
            }
            return content;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE);
        }
    }

    private boolean matchesSignature(String contentType, byte[] content, byte[] signature) {
        if (content.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) return false;
        }
        return !"image/webp".equals(contentType)
                || (content.length >= 12 && content[8] == 0x57 && content[9] == 0x45
                && content[10] == 0x42 && content[11] == 0x50);
    }

    private String normalizedPrefix() {
        return properties.profilePrefix().replaceAll("^/+|/+$", "");
    }

    private record ImageType(String extension, byte[] signature) {
    }
}
