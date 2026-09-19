// 축제/관광지 공개 댓글과 좋아요용 데이터 접근 계층.
// 설계는 docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 4장을 따른다.

import { apiClient, apiClientVoid, buildApiUrl } from './apiClient';
import {
  contentTargetSegment,
  type ContentTarget,
  type ContentTargetType,
} from './contentBookmarks';

export type { ContentTarget, ContentTargetType };

/**
 * 공개 댓글 항목.
 *
 * `memberId`는 서버가 내려주지 않는다(docs/27 4.1·6.3절). 삭제 버튼 노출은 `mine`으로만
 * 판단한다.
 *
 * `profileImageUrl`은 **로그인한 회원에게만** 온다(docs/27 6.2). 비로그인이면 항상 `null`이라
 * 화면은 닉네임 이니셜 아바타를 그린다. 직접 올린 사진이면 `/api/members/{id}/profile-image`
 * 같은 상대 경로로 오므로 `buildApiUrl`로 절대 경로를 만든다 — 프로필 화면이 자기 사진에
 * 하는 것과 같다(`memberProfile.ts`).
 */
export type ContentComment = {
  id: number;
  nickname: string;
  profileImageUrl: string | null;
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

/**
 * 서버가 준 상대 경로를 브라우저가 부를 수 있는 주소로 바꾼다.
 *
 * <p>직접 올린 사진은 `/api/members/{id}/profile-image`로, 소셜 사진은 카카오·네이버의 절대
 * URL로 온다. 절대 URL은 그대로 두고 상대 경로만 손댄다.
 */
export function resolveCommentProfileImageUrl(comment: ContentComment): ContentComment {
  if (!comment.profileImageUrl?.startsWith('/')) return comment;
  return { ...comment, profileImageUrl: buildApiUrl(comment.profileImageUrl) };
}

export const contentCommentsApi = {
  // 비로그인에도 200이다. 로그인 상태면 likedByMe/mine이 채워진다(docs/27 5.5).
  getList: (target: ContentTarget, page = 0, size = 20, signal?: AbortSignal) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return apiClient<ContentCommentPage>(
      `/api/${contentTargetSegment(target.type)}/${target.id}/comments?${params.toString()}`,
      { signal },
    ).then((page) => ({
      ...page,
      items: page.items.map(resolveCommentProfileImageUrl),
    }));
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
    ).then(resolveCommentProfileImageUrl),

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
