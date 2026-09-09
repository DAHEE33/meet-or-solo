package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.auth.service.AuthService;
import com.survey.meetorsolo.domain.content.bookmark.service.ContentBookmarkService;
import com.survey.meetorsolo.domain.content.comment.service.ContentCommentService;
import com.survey.meetorsolo.domain.festival.service.FestivalCheckinService;
import com.survey.meetorsolo.domain.matching.service.MemberWithdrawalMatchCleanupService;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberConsentCommandRepository;
import com.survey.meetorsolo.domain.member.repository.MemberPreferenceEmbeddingRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.repository.MemberTravelStyleRepository;
import com.survey.meetorsolo.external.objectstorage.ObjectStorageService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 회원 탈퇴 코어({@code docs/19} 4.4). 본인 탈퇴와 관리자 강제 탈퇴가 이 경로를 공유한다.
 *
 * <p><b>물리 삭제하지 않는다.</b> {@code members}를 참조하는 FK 31개가 전부
 * {@code ON DELETE RESTRICT}이고, 신고·제재 감사 이력과 매칭 이력이 탈퇴 회원을 참조한다.
 * 개인정보만 익명화하고 이력은 남긴다.
 *
 * <p><b>본인 탈퇴와 강제 탈퇴를 같은 상태 전이로 처리하지만 진입점은 분리한다.</b>
 * 관리자 {@code BAN}은 계정이 남아 되돌릴 수 있고 강제 탈퇴는 익명화라 되돌릴 수 없다
 * ({@code docs/19} 4.4). 그래서 {@code AdminMemberActionType}에 넣지 않는다.
 *
 * <table>
 *   <caption>데이터별 처리</caption>
 *   <tr><th>대상</th><th>처리</th></tr>
 *   <tr><td>{@code members} 개인정보</td><td>익명화. 닉네임은 표시용 고정 문구</td></tr>
 *   <tr><td>프로필 이미지 실물</td><td>commit 이후 삭제. 실패는 무시</td></tr>
 *   <tr><td>취향, 취향 임베딩, 찜</td><td>물리 삭제</td></tr>
 *   <tr><td>댓글</td><td>{@code DELETED}로 숨김</td></tr>
 *   <tr><td>동의</td><td>row 유지 + {@code revoked_at} 기록</td></tr>
 *   <tr><td>진행 중 매칭, 체크인</td><td>취소. penalty 미부과</td></tr>
 *   <tr><td>신고·제재·매칭 감사 이력</td><td>보존</td></tr>
 * </table>
 */
@Service
public class MemberWithdrawalService {

    private final Clock clock;
    private final MemberRepository members;
    private final MemberTravelStyleRepository travelStyles;
    private final MemberPreferenceEmbeddingRepository preferenceEmbeddings;
    private final MemberConsentCommandRepository consents;
    private final ContentBookmarkService bookmarks;
    private final ContentCommentService comments;
    private final MemberWithdrawalMatchCleanupService matchCleanup;
    private final FestivalCheckinService checkins;
    private final AuthService auth;
    private final ObjectStorageService objectStorage;

    public MemberWithdrawalService(
            Clock clock,
            MemberRepository members,
            MemberTravelStyleRepository travelStyles,
            MemberPreferenceEmbeddingRepository preferenceEmbeddings,
            MemberConsentCommandRepository consents,
            ContentBookmarkService bookmarks,
            ContentCommentService comments,
            MemberWithdrawalMatchCleanupService matchCleanup,
            FestivalCheckinService checkins,
            AuthService auth,
            ObjectStorageService objectStorage
    ) {
        this.clock = clock;
        this.members = members;
        this.travelStyles = travelStyles;
        this.preferenceEmbeddings = preferenceEmbeddings;
        this.consents = consents;
        this.bookmarks = bookmarks;
        this.comments = comments;
        this.matchCleanup = matchCleanup;
        this.checkins = checkins;
        this.auth = auth;
        this.objectStorage = objectStorage;
    }

    /**
     * 본인 탈퇴. 반복 호출은 조용히 성공한다.
     *
     * <p>동시 요청은 {@code findByIdForUpdate}의 row lock으로 직렬화되고, 두 번째 요청은
     * 이미 {@code WITHDRAWN}인 것을 보고 아무것도 하지 않는다.
     */
    @Transactional
    public void withdrawSelf(long memberId) {
        Member member = lock(memberId);
        if (Member.STATUS_WITHDRAWN.equals(member.getStatus())) {
            return;
        }
        withdraw(member, false, false);
    }

    /**
     * 관리자 강제 탈퇴.
     *
     * <p>이미 탈퇴한 회원이면 조용히 넘기지 않고 알린다. 관리자는 조치가 실제로 적용됐는지
     * 알아야 한다.
     *
     * @param blockRejoin 재가입을 영구 거부할지. 제재성 강제 탈퇴는 {@code true},
     *                    로그인이 막힌 회원의 탈퇴 대행은 {@code false}
     */
    @Transactional
    public void withdrawByAdmin(long memberId, boolean blockRejoin) {
        Member member = lock(memberId);
        if (Member.STATUS_WITHDRAWN.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.MEMBER_ALREADY_WITHDRAWN);
        }
        withdraw(member, true, blockRejoin);
    }

    private void withdraw(Member member, boolean byAdmin, boolean blockRejoin) {
        long memberId = member.getId();
        OffsetDateTime now = OffsetDateTime.now(clock);
        // 익명화하면 읽을 수 없으므로 object key를 먼저 확보한다.
        String profileImageObjectKey = member.getProfileImageObjectKey();

        matchCleanup.cleanUp(memberId, now);
        checkins.cancelAllOnWithdrawal(memberId);
        comments.softDeleteAllOnWithdrawal(memberId);
        bookmarks.deleteAllOnWithdrawal(memberId);
        travelStyles.deleteAllByMemberId(memberId);
        preferenceEmbeddings.deleteByMemberId(memberId);
        consents.revokeAllOnWithdrawal(memberId, now);

        try {
            member.withdraw(now, byAdmin, blockRejoin);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.MEMBER_INACTIVE, exception.getMessage());
        }
        members.saveAndFlush(member);

        // refresh token 폐기와 WebSocket session 종료. 로그아웃과 같은 경로다.
        auth.revokeSession(memberId);
        registerObjectCleanup(profileImageObjectKey);
    }

    private Member lock(long memberId) {
        return members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 프로필 이미지 실물은 commit 이후에 지운다. 저장소 삭제 실패로 탈퇴를 되돌리지 않는다.
     * {@code ObjectStorageService.delete}는 null과 저장소 장애를 모두 무시한다.
     */
    private void registerObjectCleanup(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectStorage.delete(objectKey);
            }
        });
    }
}
