import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import ContentCommentSection, { handleCommentDeleteDialogKeyDown } from './ContentCommentSection';
import ContentCommentForm from './ContentCommentForm';
import ContentCommentItem from './ContentCommentItem';
import BookmarkButton from '../common/BookmarkButton';
import type { ContentComment } from '../../api/contentComments';

// useEffect가 돌지 않는 SSR 마크업이므로 초기 상태(LOADING)가 그대로 나온다.
// jsdom이 없어 클릭·비동기 갱신을 재현할 수 없으므로 마크업 수준에서 계약을 확인한다.
const render = (element: React.ReactElement) =>
  renderToStaticMarkup(<MemoryRouter>{element}</MemoryRouter>);

const comment = (overrides: Partial<ContentComment> = {}): ContentComment => ({
  id: 1024,
  nickname: '춘천사람',
  body: '작년에도 갔는데 야간 조명이 좋았어요',
  likeCount: 4,
  likedByMe: false,
  mine: false,
  createdAt: new Date().toISOString(),
  ...overrides,
});

describe('ContentCommentSection', () => {
  it('댓글 수 헤더와 입력 영역을 함께 그린다', () => {
    const html = render(
      <ContentCommentSection target={{ type: 'FESTIVAL', id: 298 }} loggedIn admin={false} initialCount={12} />,
    );
    expect(html).toContain('댓글');
    expect(html).toContain('12');
    expect(html).toContain('댓글을 남겨보세요');
  });

  it('비로그인 사용자에게는 입력창 대신 로그인 링크를 준다', () => {
    const html = render(
      <ContentCommentSection target={{ type: 'TOUR_PLACE', id: 7 }} loggedIn={false} admin={false} />,
    );
    expect(html).toContain('로그인하고 댓글 남기기');
    expect(html).toContain('href="/login"');
    expect(html).not.toContain('<textarea');
  });
});

describe('ContentCommentForm', () => {
  const noop = () => {};
  const submit = () => Promise.resolve(true);

  it('로그인 상태에서는 500자 제한 입력창과 글자수를 보여준다', () => {
    const html = render(
      <ContentCommentForm loggedIn submitting={false} bodyError={null} onSubmit={submit} onChangeBody={noop} />,
    );
    expect(html).toContain('maxLength="500"');
    expect(html).toContain('0/500');
    expect(html).toContain('등록');
  });

  it('본문 검증 실패 메시지를 alert으로 노출한다', () => {
    const html = render(
      <ContentCommentForm loggedIn submitting={false} bodyError="댓글을 입력해주세요." onSubmit={submit} onChangeBody={noop} />,
    );
    expect(html).toContain('role="alert"');
    expect(html).toContain('댓글을 입력해주세요.');
  });
});

describe('ContentCommentItem', () => {
  const handlers = { onToggleLike: () => {}, onRequestDelete: () => {}, onHide: () => {} };

  it('닉네임 이니셜 아바타와 좋아요 수를 그리고 프로필 이미지는 쓰지 않는다', () => {
    const html = render(
      <ContentCommentItem comment={comment()} loggedIn admin={false} likePending={false} {...handlers} />,
    );
    expect(html).toContain('춘천사람');
    expect(html).toContain('>춘<');
    expect(html).toContain('aria-label="좋아요"');
    expect(html).toContain('>4<');
    expect(html).not.toContain('<img');
  });

  it('내 댓글에만 삭제 버튼을 노출한다', () => {
    const mine = render(
      <ContentCommentItem comment={comment({ mine: true })} loggedIn admin={false} likePending={false} {...handlers} />,
    );
    const others = render(
      <ContentCommentItem comment={comment()} loggedIn admin={false} likePending={false} {...handlers} />,
    );
    expect(mine).toContain('내 댓글 삭제');
    expect(others).not.toContain('내 댓글 삭제');
  });

  it('관리자에게만 남의 댓글 숨김 버튼을 노출한다', () => {
    const asAdmin = render(
      <ContentCommentItem comment={comment()} loggedIn admin likePending={false} {...handlers} />,
    );
    const asMember = render(
      <ContentCommentItem comment={comment()} loggedIn admin={false} likePending={false} {...handlers} />,
    );
    expect(asAdmin).toContain('숨김');
    expect(asMember).not.toContain('숨김');
  });

  it('비로그인 사용자에게는 좋아요 버튼을 비활성화한다', () => {
    const html = render(
      <ContentCommentItem comment={comment()} loggedIn={false} admin={false} likePending={false} {...handlers} />,
    );
    expect(html).toContain('disabled');
  });

  it('좋아요한 댓글은 눌린 상태로 표시한다', () => {
    const html = render(
      <ContentCommentItem comment={comment({ likedByMe: true })} loggedIn admin={false} likePending={false} {...handlers} />,
    );
    expect(html).toContain('aria-pressed="true"');
    expect(html).toContain('aria-label="좋아요 취소"');
  });
});

describe('BookmarkButton', () => {
  it('찜 상태에 따라 라벨과 눌린 상태가 바뀐다', () => {
    const off = renderToStaticMarkup(<BookmarkButton bookmarked={false} onClick={() => {}} />);
    const on = renderToStaticMarkup(<BookmarkButton bookmarked onClick={() => {}} />);
    expect(off).toContain('aria-label="찜하기"');
    expect(off).toContain('aria-pressed="false"');
    expect(on).toContain('aria-label="찜 해제"');
    expect(on).toContain('aria-pressed="true"');
    expect(on).toContain('fill-coral');
  });

  it('engagement 조회 전에는 누를 수 없다', () => {
    const html = renderToStaticMarkup(<BookmarkButton bookmarked={false} disabled onClick={() => {}} />);
    expect(html).toContain('disabled');
  });
});

describe('handleCommentDeleteDialogKeyDown', () => {
  it('삭제 중이 아니면 Escape로 닫는다', () => {
    const preventDefault = vi.fn();
    const onClose = vi.fn();
    handleCommentDeleteDialogKeyDown({ key: 'Escape', shiftKey: false, preventDefault }, [], null, false, onClose);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('삭제 중에는 Escape를 무시한다', () => {
    const onClose = vi.fn();
    handleCommentDeleteDialogKeyDown({ key: 'Escape', shiftKey: false, preventDefault: vi.fn() }, [], null, true, onClose);
    expect(onClose).not.toHaveBeenCalled();
  });

  it('Tab은 focusable 목록 안에서 순환한다', () => {
    const first = { focus: vi.fn() } as unknown as HTMLElement;
    const last = { focus: vi.fn() } as unknown as HTMLElement;
    handleCommentDeleteDialogKeyDown({ key: 'Tab', shiftKey: false, preventDefault: vi.fn() }, [first, last], last, false, vi.fn());
    expect(first.focus).toHaveBeenCalledOnce();
    handleCommentDeleteDialogKeyDown({ key: 'Tab', shiftKey: true, preventDefault: vi.fn() }, [first, last], first, false, vi.fn());
    expect(last.focus).toHaveBeenCalledOnce();
  });
});
