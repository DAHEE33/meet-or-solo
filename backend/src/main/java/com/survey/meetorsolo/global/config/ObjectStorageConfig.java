package com.survey.meetorsolo.global.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfig {

    @Bean
    S3Client objectStorageS3Client(ObjectStorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(required(properties.endpoint(), "OCI_OBJECT_STORAGE_ENDPOINT")))
                .region(Region.of(required(properties.region(), "OCI_OBJECT_STORAGE_REGION")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        required(properties.accessKey(), "OCI_OBJECT_STORAGE_ACCESS_KEY"),
                        required(properties.secretKey(), "OCI_OBJECT_STORAGE_SECRET_KEY")
                )))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    private String required(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
