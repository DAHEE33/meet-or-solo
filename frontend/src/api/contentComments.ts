// 축제/관광지 공개 댓글과 좋아요용 데이터 접근 계층.
// 설계는 docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 4장을 따른다.

import { apiClient, apiClientVoid } from './apiClient';
import {
  contentTargetSegment,
  type ContentTarget,
  type ContentTargetType,
} from './contentBookmarks';

export type { ContentTarget, ContentTargetType };

/**
 * 공개 댓글 항목.
 *
 * `memberId`와 `profileImageUrl`은 서버가 내려주지 않는다(docs/27 4.1·6.2절). 삭제 버튼 노출은
 * `mine`으로만 판단하고, 작성자 표시는 닉네임과 이니셜 아바타만 쓴다.
 */
export type ContentComment = {
  id: number;
  nickname: string;
  body: string;
  likeCount: number;
  likedByMe: boolean;
  mine: boolean;
  createdAt: string;
};

export type ContentCommentPage = {
  items: ContentComment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type ContentCommentLikeResult = {
  liked: boolean;
  likeCount: number;
};

export const contentCommentsApi = {
  // 비로그인에도 200이다. 로그인 상태면 likedByMe/mine이 채워진다(docs/27 5.5).
  getList: (target: ContentTarget, page = 0, size = 20, signal?: AbortSignal) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return apiClient<ContentCommentPage>(
      `/api/${contentTargetSegment(target.type)}/${target.id}/comments?${params.toString()}`,
      { signal },
    );
  },

  create: (target: ContentTarget, body: string, signal?: AbortSignal) =>
    apiClient<ContentComment>(
      `/api/${contentTargetSegment(target.type)}/${target.id}/comments`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ body }),
        signal,
      },
    ),

  // 서버는 soft delete지만 계약은 리소스 제거이므로 body 없는 204다. 이미 삭제된 댓글도 204다.
  remove: (commentId: number, signal?: AbortSignal) =>
    apiClientVoid(`/api/comments/${commentId}`, { method: 'DELETE', signal }),

  setLike: (commentId: number, liked: boolean, signal?: AbortSignal) =>
    apiClient<ContentCommentLikeResult>(`/api/comments/${commentId}/like`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ liked }),
      signal,
    }),

  // 관리자 숨김. 별도 관리 화면 없이 공개 댓글 섹션에서 관리자에게만 노출한다(docs/27 2.3).
  setVisibility: (commentId: number, visible: boolean, signal?: AbortSignal) =>
    apiClient<{ visible: boolean }>(`/api/admin/comments/${commentId}/visibility`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ visible }),
      signal,
    }),
};
