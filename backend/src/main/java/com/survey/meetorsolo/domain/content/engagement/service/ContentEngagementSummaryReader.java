package com.survey.meetorsolo.domain.content.engagement.service;

import com.survey.meetorsolo.domain.content.bookmark.repository.ContentBookmarkRepository;
import com.survey.meetorsolo.domain.content.comment.repository.ContentCommentRepository;
import com.survey.meetorsolo.domain.content.support.ContentTargetCountRow;
import com.survey.meetorsolo.domain.content.support.ContentTargetType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목록 여러 건의 찜 수·댓글 수와 "내가 찜했는지"를 한 번에 모은다.
 *
 * <p><b>이 클래스는 절대 인증 예외를 던지지 않는다.</b> 공개 목록에서 쓰이므로 비로그인
 * ({@code viewerMemberId == null})이면 찜 여부가 모두 {@code false}일 뿐이다
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.1).
 *
 * <p>찜 수·댓글 수를 여기서도 셀 수 있지만 <b>목록 조회는 쓰지 않는다</b> — 목록은 그 값으로
 * 정렬까지 해야 해서 native query 안에서 집계한다(docs/25 14.5). 이 클래스의 집계는 정렬
 * 기준이 거리인 반경 검색(주변에서 열리는 축제)처럼, 결과가 먼저 정해지는 조회를 위한 것이다.
 */
@Component
public class ContentEngagementSummaryReader {

    private final ContentBookmarkRepository bookmarks;
    private final ContentCommentRepository comments;

    public ContentEngagementSummaryReader(
            ContentBookmarkRepository bookmarks,
            ContentCommentRepository comments
    ) {
        this.bookmarks = bookmarks;
        this.comments = comments;
    }

    /** 대상 1건의 목록 표시용 집계. */
    public record Summary(long bookmarkCount, long commentCount, boolean bookmarkedByMe) {

        public static final Summary EMPTY = new Summary(0, 0, false);
    }

    /**
     * 이 회원이 찜한 대상 id만 골라낸다. 목록 조회처럼 찜 수를 이미 알고 있는 경로가 쓴다.
     *
     * @param viewerMemberId 비로그인이면 {@code null}
     */
    @Transactional(readOnly = true)
    public Set<Long> bookmarkedIds(
            ContentTargetType type,
            Collection<Long> targetIds,
            Long viewerMemberId
    ) {
        if (viewerMemberId == null || targetIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = type == ContentTargetType.FESTIVAL
                ? bookmarks.findBookmarkedFestivalIds(viewerMemberId, targetIds)
                : bookmarks.findBookmarkedTourPlaceIds(viewerMemberId, targetIds);
        return Set.copyOf(ids);
    }

    /**
     * 찜 수·댓글 수·내 찜 여부를 한 번에 모은다. 조회 건수와 무관하게 쿼리 3건(비로그인은 2건)
     * 이라 항목마다 세는 N+1이 생기지 않는다.
     *
     * @return 대상 id별 집계. 찜·댓글이 하나도 없는 대상은 결과에 없으므로 호출부가
     *         {@link Summary#EMPTY}로 받아야 한다
     */
    @Transactional(readOnly = true)
    public Map<Long, Summary> summarize(
            ContentTargetType type,
            Collection<Long> targetIds,
            Long viewerMemberId
    ) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> bookmarkCounts = toMap(type == ContentTargetType.FESTIVAL
                ? bookmarks.countByFestivalIds(targetIds)
                : bookmarks.countByTourPlaceIds(targetIds));
        Map<Long, Long> commentCounts = toMap(type == ContentTargetType.FESTIVAL
                ? comments.countVisibleByFestivalIds(targetIds)
                : comments.countVisibleByTourPlaceIds(targetIds));
        Set<Long> bookmarked = bookmarkedIds(type, targetIds, viewerMemberId);

        return targetIds.stream().distinct().collect(Collectors.toMap(
                Function.identity(),
                targetId -> new Summary(
                        bookmarkCounts.getOrDefault(targetId, 0L),
                        commentCounts.getOrDefault(targetId, 0L),
                        bookmarked.contains(targetId)
                )
        ));
    }

    private Map<Long, Long> toMap(List<ContentTargetCountRow> rows) {
        return rows.stream().collect(Collectors.toMap(
                ContentTargetCountRow::targetId,
                ContentTargetCountRow::count
        ));
    }
}
