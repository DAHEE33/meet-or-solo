package com.survey.meetorsolo.domain.content.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentLikeResponse;
import com.survey.meetorsolo.domain.content.comment.entity.ContentComment;
import com.survey.meetorsolo.domain.content.comment.entity.ContentCommentStatus;
import com.survey.meetorsolo.domain.content.comment.repository.ContentCommentLikeRepository;
import com.survey.meetorsolo.domain.content.comment.repository.ContentCommentRepository;
import com.survey.meetorsolo.domain.content.support.ContentTarget;
import com.survey.meetorsolo.domain.content.support.ContentTargetReader;
import com.survey.meetorsolo.domain.festival.repository.FestivalCheckinRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentCommentServiceTest {

    private static final long MEMBER_ID = 9_110_001L;
    private static final long OTHER_MEMBER_ID = 9_110_002L;
    private static final long COMMENT_ID = 9_130_001L;
    private static final ContentTarget FESTIVAL = ContentTarget.festival(9_100_001L);
    private static final ContentTarget TOUR_PLACE = ContentTarget.tourPlace(9_100_002L);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-07T12:00:00+09:00");

    private final ContentCommentRepository comments = mock(ContentCommentRepository.class);
    private final ContentCommentLikeRepository likes = mock(ContentCommentLikeRepository.class);
    private final ContentTargetReader targets = mock(ContentTargetReader.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final FestivalCheckinRepository checkins = mock(FestivalCheckinRepository.class);
    private final Clock clock = Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"));

    private ContentCommentService service;

    @BeforeEach
    void setUp() {
        service = new ContentCommentService(comments, likes, targets, members, checkins, clock);
    }

    /**
     * 축제 댓글은 그 축제에 체크인한 적 있는 회원만 쓸 수 있다(docs/27 5.2). 작성 성공을 보는
     * 테스트는 이 조건을 먼저 만족시켜야 뒤 단계에 도달한다.
     */
    private void givenCheckedIn() {
        when(checkins.existsByMemberIdAndFestivalId(MEMBER_ID, FESTIVAL.id())).thenReturn(true);
    }

    private Member activeMember() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(Member.STATUS_ACTIVE);
        when(member.getNickname()).thenReturn("춘천사람");
        return member;
    }

    // --- docs/27 5.2: 작성 자격과 본문 검증 -------------------------------------------------

    @Test
    void 닉네임이_없는_PROFILE_REQUIRED_회원은_댓글을_남길_수_없다() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(Member.STATUS_PROFILE_REQUIRED);
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.create(MEMBER_ID, FESTIVAL, "안녕하세요"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_COMMENT_PROFILE_REQUIRED);

        verify(comments, never()).save(any());
    }

    @Test
    void 공백만_있는_본문은_거절한다() {
        Member member = activeMember();
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.create(MEMBER_ID, FESTIVAL, "   \n  "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_COMMENT_INVALID_REQUEST);

        verify(comments, never()).save(any());
    }

    @Test
    void 본문이_500자를_넘으면_거절한다() {
        Member member = activeMember();
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        String tooLong = "가".repeat(ContentComment.BODY_MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.create(MEMBER_ID, FESTIVAL, tooLong))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_COMMENT_INVALID_REQUEST);
    }

    // --- docs/27 5.2: 축제 댓글은 체크인한 사람만 -----------------------------------------

    @Test
    void 체크인한_적_없는_축제에는_댓글을_남길_수_없다() {
        Member member = activeMember();
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(checkins.existsByMemberIdAndFestivalId(MEMBER_ID, FESTIVAL.id())).thenReturn(false);

        assertThatThrownBy(() -> service.create(MEMBER_ID, FESTIVAL, "가본 적 없는 축제 후기"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_COMMENT_CHECKIN_REQUIRED);

        verify(comments, never()).save(any());
    }

    @Test
    void 체크인_이력이_있으면_지금_체크인_중이_아니어도_댓글을_남길_수_있다() {
        // 체크인 유효기간이 1시간이라 상태를 보면 현장을 떠난 순간 후기를 못 쓴다. 그래서
        // repository가 상태를 보지 않고 "행이 있는가"만 판정한다.
        Member member = activeMember();
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        givenCheckedIn();
        when(comments.findLatestCreatedAtByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(comments.save(any(ContentComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(MEMBER_ID, FESTIVAL, "다녀왔어요").body()).isEqualTo("다녀왔어요");
    }

    @Test
    void 관광지는_체크인_기능이_없으므로_체크인_없이_댓글을_남길_수_있다() {
        Member member = activeMember();
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(comments.findLatestCreatedAtByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(comments.save(any(ContentComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(MEMBER_ID, TOUR_PLACE, "좋았어요").body()).isEqualTo("좋았어요");
        verify(checkins, never()).existsByMemberIdAndFestivalId(anyLong(), anyLong());
    }

    @Test
    void canComment는_비로그인이면_대상과_무관하게_false다() {
        assertThat(service.canComment(null, FESTIVAL)).isFalse();
        assertThat(service.canComment(null, TOUR_PLACE)).isFalse();
    }

    @Test
    void canComment는_축제면_체크인_이력을_관광지면_로그인_여부를_따른다() {
        when(checkins.existsByMemberIdAndFestivalId(MEMBER_ID, FESTIVAL.id())).thenReturn(false);

        assertThat(service.canComment(MEMBER_ID, FESTIVAL)).isFalse();
        assertThat(service.canComment(MEMBER_ID, TOUR_PLACE)).isTrue();

        when(checkins.existsByMemberIdAndFestivalId(MEMBER_ID, FESTIVAL.id())).thenReturn(true);
        assertThat(service.canComment(MEMBER_ID, FESTIVAL)).isTrue();
    }

    // --- docs/27 10-7: 도배 완화 5초 규칙 ----------------------------------------------------

    @Test
    void 마지막_댓글로부터_5초_이내면_429다() {
        Member member = activeMember();
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        givenCheckedIn();
        when(comments.findLatestCreatedAtByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(NOW.minusSeconds(4)));

        assertThatThrownBy(() -> service.create(MEMBER_ID, FESTIVAL, "연타"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_COMMENT_TOO_FREQUENT);

        verify(comments, never()).save(any());
    }

    @Test
    void 마지막_댓글로부터_5초가_지나면_등록한다() {
        Member member = activeMember();
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        givenCheckedIn();
        when(comments.findLatestCreatedAtByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(NOW.minusSeconds(5)));
        when(comments.save(any(ContentComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(MEMBER_ID, FESTIVAL, "  정상 댓글  ").body())
                .isEqualTo("정상 댓글");
        verify(comments).save(any(ContentComment.class));
    }

    @Test
    void 첫_댓글은_도배_규칙에_걸리지_않는다() {
        Member member = activeMember();
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        givenCheckedIn();
        when(comments.findLatestCreatedAtByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(comments.save(any(ContentComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(MEMBER_ID, FESTIVAL, "첫 댓글").mine()).isTrue();
    }

    // --- docs/27 2.2: 좋아요 카운터는 실제 영향 행 수로만 움직인다 --------------------------

    private ContentComment visibleComment(long authorMemberId) {
        ContentComment comment = mock(ContentComment.class);
        when(comment.getStatus()).thenReturn(ContentCommentStatus.VISIBLE);
        when(comment.getMemberId()).thenReturn(authorMemberId);
        return comment;
    }

    @Test
    void 좋아요_INSERT가_1행을_넣으면_카운터를_올린다() {
        ContentComment comment = visibleComment(OTHER_MEMBER_ID);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(likes.insertIgnoringConflict(eq(COMMENT_ID), eq(MEMBER_ID), any())).thenReturn(1);
        when(comments.findLikeCountById(COMMENT_ID)).thenReturn(Optional.of(1));

        ContentCommentLikeResponse response = service.toggleLike(MEMBER_ID, COMMENT_ID, true);

        assertThat(response).isEqualTo(new ContentCommentLikeResponse(true, 1));
        verify(comments).increaseLikeCount(eq(COMMENT_ID), any());
    }

    @Test
    void 이미_눌린_좋아요는_카운터를_올리지_않는다() {
        ContentComment comment = visibleComment(OTHER_MEMBER_ID);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(likes.insertIgnoringConflict(eq(COMMENT_ID), eq(MEMBER_ID), any())).thenReturn(0);
        when(comments.findLikeCountById(COMMENT_ID)).thenReturn(Optional.of(1));

        assertThat(service.toggleLike(MEMBER_ID, COMMENT_ID, true).likeCount()).isEqualTo(1);

        verify(comments, never()).increaseLikeCount(anyLong(), any());
    }

    @Test
    void 좋아요_DELETE가_1행을_지우면_카운터를_내린다() {
        ContentComment comment = visibleComment(OTHER_MEMBER_ID);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(likes.deleteByCommentIdAndMemberId(COMMENT_ID, MEMBER_ID)).thenReturn(1);
        when(comments.findLikeCountById(COMMENT_ID)).thenReturn(Optional.of(0));

        assertThat(service.toggleLike(MEMBER_ID, COMMENT_ID, false))
                .isEqualTo(new ContentCommentLikeResponse(false, 0));
        verify(comments).decreaseLikeCount(eq(COMMENT_ID), any());
    }

    @Test
    void 누른_적_없는_좋아요_해제는_카운터를_내리지_않는다() {
        ContentComment comment = visibleComment(OTHER_MEMBER_ID);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(likes.deleteByCommentIdAndMemberId(COMMENT_ID, MEMBER_ID)).thenReturn(0);
        when(comments.findLikeCountById(COMMENT_ID)).thenReturn(Optional.of(0));

        assertThat(service.toggleLike(MEMBER_ID, COMMENT_ID, false).likeCount()).isZero();

        verify(comments, never()).decreaseLikeCount(anyLong(), any());
    }

    @Test
    void 삭제된_댓글에는_좋아요를_누를_수_없다() {
        ContentComment deleted = mock(ContentComment.class);
        when(deleted.getStatus()).thenReturn(ContentCommentStatus.DELETED);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.toggleLike(MEMBER_ID, COMMENT_ID, true))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_COMMENT_NOT_FOUND);

        verify(likes, never()).insertIgnoringConflict(anyLong(), anyLong(), any());
    }

    // --- docs/27 5.3, 10-5: 삭제 권한과 멱등성 ----------------------------------------------

    @Test
    void 남의_댓글_삭제는_FORBIDDEN이다() {
        when(comments.softDeleteByAuthor(eq(COMMENT_ID), eq(MEMBER_ID), any())).thenReturn(0);
        ContentComment comment = visibleComment(OTHER_MEMBER_ID);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.delete(MEMBER_ID, COMMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_COMMENT_FORBIDDEN);
    }

    @Test
    void 없는_댓글_삭제는_NOT_FOUND다() {
        when(comments.softDeleteByAuthor(eq(COMMENT_ID), eq(MEMBER_ID), any())).thenReturn(0);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(MEMBER_ID, COMMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_COMMENT_NOT_FOUND);
    }

    @Test
    void 이미_삭제한_본인_댓글을_다시_삭제해도_성공한다() {
        ContentComment alreadyDeleted = mock(ContentComment.class);
        when(alreadyDeleted.getStatus()).thenReturn(ContentCommentStatus.DELETED);
        when(alreadyDeleted.getMemberId()).thenReturn(MEMBER_ID);
        when(comments.softDeleteByAuthor(eq(COMMENT_ID), eq(MEMBER_ID), any())).thenReturn(0);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(alreadyDeleted));

        service.delete(MEMBER_ID, COMMENT_ID);
    }

    @Test
    void 본인_댓글_삭제가_성공하면_추가_조회를_하지_않는다() {
        when(comments.softDeleteByAuthor(eq(COMMENT_ID), eq(MEMBER_ID), any())).thenReturn(1);

        service.delete(MEMBER_ID, COMMENT_ID);

        verify(comments, never()).findById(anyLong());
    }

    // --- docs/27 5.5: 비로그인 목록 -----------------------------------------------------------

    @Test
    void 비로그인_목록_조회는_좋아요_조회를_하지_않는다() {
        when(comments.findVisibleByFestivalId(eq(FESTIVAL.id()), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(service.getComments(null, FESTIVAL, 0, 20).items()).isEmpty();

        verify(likes, never()).findLikedCommentIds(anyLong(), any());
    }

    @Test
    void 탈퇴_일괄_숨김은_고정_시각으로_전환한다() {
        when(comments.softDeleteAllByMemberId(MEMBER_ID, NOW)).thenReturn(3);

        assertThat(service.softDeleteAllOnWithdrawal(MEMBER_ID)).isEqualTo(3);
        verify(comments).softDeleteAllByMemberId(MEMBER_ID, NOW);
    }

    @Test
    void 고정_Clock이_서울_기준_현재시각을_준다() {
        assertThat(OffsetDateTime.now(clock).toInstant()).isEqualTo(Instant.parse("2026-09-07T03:00:00Z"));
    }
}
