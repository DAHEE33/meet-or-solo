import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import LoginPage from './LoginPage';
import AccountRestrictionNotice from '../components/common/AccountRestrictionNotice';
import type { SanctionNotice } from '../api/types';

const page = (search: string) => renderToStaticMarkup(
  <MemoryRouter initialEntries={[`/login${search}`]}><LoginPage /></MemoryRouter>,
);

function notice(overrides: Partial<SanctionNotice> = {}): SanctionNotice {
  return {
    status: 'SUSPENDED',
    suspendedUntil: '2026-09-15T10:00:00+09:00',
    reasonCode: 'HARASSMENT',
    reasonMessage: '다른 이용자에 대한 부적절한 언행',
    contactEmail: null,
    rejoinAvailableAt: null,
    ...overrides,
  };
}

describe('LoginPage', () => {
  it('평상시에는 소셜 로그인 버튼과 약관 안내를 보여준다', () => {
    const html = page('');
    expect(html).toContain('카카오로 시작하기');
    expect(html).toContain('네이버로 시작하기');
    expect(html).toContain('이용약관');
  });

  it('일반 OAuth 실패는 재시도 안내를 유지한다', () => {
    const html = page('?oauthError=oauth_failed');
    expect(html).toContain('잠시 후 다시 시도해 주세요');
    expect(html).toContain('카카오로 시작하기');
  });

  /**
   * 제재된 계정은 다시 로그인해도 같은 화면으로 돌아오므로 재시도 안내와 로그인 버튼을
   * 함께 두면 사용자가 무한히 재시도한다.
   */
  it('제재 안내에서는 재시도 안내와 로그인 버튼을 감춘다', () => {
    // useEffect가 돌지 않는 SSR 마크업이라 안내 조회 전 포괄 문구가 나온다.
    const html = page('?oauthError=account_restricted');
    expect(html).toContain('이 계정으로는 지금 로그인할 수 없습니다');
    // 탈퇴도 이 화면으로 오므로 포괄 문구가 "제재"라고 단정하면 틀린 안내가 된다.
    expect(html).not.toContain('제재되어');
    expect(html).not.toContain('잠시 후 다시 시도해 주세요');
    expect(html).not.toContain('카카오로 시작하기');
    expect(html).not.toContain('네이버로 시작하기');
  });

  it('영구정지 안내만 로그인 화면에 뜬다', () => {
    // 정지 회원은 로그인이 되므로 이 화면을 보지 않는다. 정지 안내는 앱 안 dialog가 맡는다.
    const html = page('?oauthError=account_restricted');
    expect(html).toContain('이 계정으로는 지금 로그인할 수 없습니다');
  });

  it('제재 사유를 URL에서 읽지 않는다', () => {
    // 사유·기간은 notice cookie로만 온다. query parameter를 신뢰하면 위조된 안내를 띄울 수 있다.
    const html = page('?oauthError=account_restricted&reason=HARASSMENT&until=2026-09-15');
    expect(html).not.toContain('부적절한 언행');
    expect(html).not.toContain('2026-09-15');
  });
});

describe('AccountRestrictionNotice', () => {
  it('이용정지는 사유와 종료 시각, 막히는 활동을 함께 보여준다', () => {
    const html = renderToStaticMarkup(<AccountRestrictionNotice notice={notice()} />);
    expect(html).toContain('이용정지 중이에요');
    expect(html).toContain('다른 이용자에 대한 부적절한 언행');
    expect(html).toContain('2026-09-15 10:00:00까지');
    // 정지는 조회를 막지 않으므로 무엇이 막히는지 알려줘야 한다.
    expect(html).toContain('체크인, 동행 매칭, 댓글 작성을 할 수 없어요');
    expect(html).toContain('둘러보기는 그대로 이용할 수 있어요');
  });

  it('영구정지는 기간 대신 영구임을 알린다', () => {
    const html = renderToStaticMarkup(
      <AccountRestrictionNotice notice={notice({ status: 'BANNED', suspendedUntil: null })} />,
    );
    expect(html).toContain('영구정지된 계정이에요');
    expect(html).toContain('기간 제한 없음');
    expect(html).toContain('다시 로그인할 수 없어요');
  });

  /** 신고자 보호. 화면이 신고 건수·시점을 덧붙이지 않는지 확인한다. */
  it('신고 건수나 신고자를 추정할 수 있는 문구를 덧붙이지 않는다', () => {
    const html = renderToStaticMarkup(<AccountRestrictionNotice notice={notice()} />);
    expect(html).not.toContain('신고');
    expect(html).not.toContain('누적');
    expect(html).not.toMatch(/\d+건/);
  });

  it('고객센터 이메일이 없으면 문의 문구를 숨긴다', () => {
    expect(renderToStaticMarkup(<AccountRestrictionNotice notice={notice()} />))
      .not.toContain('고객센터 이메일');
  });

  it('고객센터 이메일을 주소까지 그대로 보여주고 mailto로 걸어준다', () => {
    const html = renderToStaticMarkup(
      <AccountRestrictionNotice notice={notice({ contactEmail: 'support@example.test' })} />,
    );

    expect(html).toContain('고객센터 이메일로 연락주세요');
    // 주소가 문구에 보여야 메일 앱이 없는 환경에서도 옮겨 적을 수 있다.
    expect(html).toContain('support@example.test');
    expect(html).toContain('mailto:support@example.test');
  });

  it('영구정지와 이용정지의 문의 안내 문구가 다르다', () => {
    const banned = renderToStaticMarkup(
      <AccountRestrictionNotice
        notice={notice({ status: 'BANNED', suspendedUntil: null, contactEmail: 'a@b.test' })}
      />,
    );
    const suspended = renderToStaticMarkup(
      <AccountRestrictionNotice notice={notice({ contactEmail: 'a@b.test' })} />,
    );

    expect(banned).toContain('제재에 이의가 있으면');
    expect(suspended).toContain('제재 사유가 잘못되었다고 생각되면');
  });
});
