package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberProfileResponse;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.external.objectstorage.ObjectStorageService;
import com.survey.meetorsolo.external.objectstorage.StoredObject;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MemberProfileImageService {

    private final MemberRepository memberRepository;
    private final MemberProfileService memberProfileService;
    private final ObjectStorageService objectStorageService;

    public MemberProfileImageService(
            MemberRepository memberRepository,
            MemberProfileService memberProfileService,
            ObjectStorageService objectStorageService
    ) {
        this.memberRepository = memberRepository;
        this.memberProfileService = memberProfileService;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public MemberProfileResponse upload(Long memberId, MultipartFile file) {
        Member member = findMember(memberId);
        String previousObjectKey = member.getProfileImageObjectKey();
        String newObjectKey = objectStorageService.uploadProfileImage(memberId, file);
        registerObjectCleanup(previousObjectKey, newObjectKey);
        member.updateProfileImageObjectKey(newObjectKey);
        memberRepository.saveAndFlush(member);
        return memberProfileService.getProfile(memberId);
    }

    @Transactional(readOnly = true)
    public StoredObject download(Long memberId) {
        Member member = findMember(memberId);
        if (member.getProfileImageObjectKey() == null || member.getProfileImageObjectKey().isBlank()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }
        return objectStorageService.download(member.getProfileImageObjectKey());
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void registerObjectCleanup(String previousObjectKey, String newObjectKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectStorageService.delete(previousObjectKey);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    objectStorageService.delete(newObjectKey);
                }
            }
        });
    }
}
