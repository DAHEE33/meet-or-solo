import { describe, expect, it, vi } from 'vitest';
import {
  createListBookmarkSession,
  initialListBookmarkState,
  resolveBookmarkCount,
  resolveBookmarked,
  targetKey,
  type ListBookmarkState,
} from './useListBookmarks';
import type { ContentTarget } from '../api/contentBookmarks';

const FESTIVAL: ContentTarget = { type: 'FESTIVAL', id: 298 };
const SPOT: ContentTarget = { type: 'TOUR_PLACE', id: 7 };

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((ok, no) => {
    resolve = ok;
    reject = no;
  });
  return { promise, resolve, reject };
};

const session = (loggedIn: boolean, setBookmark: ReturnType<typeof vi.fn>) => {
  const states: ListBookmarkState[] = [];
  const goToLogin = vi.fn();
  const controls = createListBookmarkSession(() => loggedIn, (state) => states.push(state), {
    setBookmark,
    goToLogin,
  });
  return { controls, states, goToLogin, last: () => states[states.length - 1] };
};

describe('resolveBookmarked', () => {
  it('내가 바꾼 적이 없으면 목록 응답 값을 그대로 쓴다', () => {
    const state = initialListBookmarkState();

    expect(resolveBookmarked(state, FESTIVAL, true)).toBe(true);
    expect(resolveBookmarked(state, FESTIVAL, false)).toBe(false);
  });

  it('내가 바꿨으면 그 값이 목록 응답을 덮는다', () => {
    const state: ListBookmarkState = {
      overrides: { [targetKey(FESTIVAL)]: true },
      pendingKey: null,
    };

    expect(resolveBookmarked(state, FESTIVAL, false)).toBe(true);
    // 같은 id라도 종류가 다르면 다른 대상이다.
    expect(resolveBookmarked(state, { type: 'TOUR_PLACE', id: 298 }, false)).toBe(false);
  });
});

describe('resolveBookmarkCount', () => {
  it('내가 찜하면 개수도 1 오른다', () => {
    // 목록을 다시 부르지 않으므로, 내가 누른 하트가 숫자에 반영되지 않으면 눌리지 않은 것처럼 보인다.
    const state: ListBookmarkState = { overrides: { [targetKey(FESTIVAL)]: true }, pendingKey: null };

    expect(resolveBookmarkCount(state, FESTIVAL, { bookmarked: false, bookmarkCount: 4 })).toBe(5);
  });

  it('내가 찜을 풀면 개수도 1 내린다', () => {
    const state: ListBookmarkState = { overrides: { [targetKey(FESTIVAL)]: false }, pendingKey: null };

    expect(resolveBookmarkCount(state, FESTIVAL, { bookmarked: true, bookmarkCount: 4 })).toBe(3);
  });

  it('토글했다가 원래 상태로 돌아오면 서버가 준 수 그대로다', () => {
    const state: ListBookmarkState = { overrides: { [targetKey(FESTIVAL)]: true }, pendingKey: null };

    expect(resolveBookmarkCount(state, FESTIVAL, { bookmarked: true, bookmarkCount: 4 })).toBe(4);
  });

  it('0건에서 찜을 푸는 어긋난 상태에서도 음수가 되지 않는다', () => {
    const state: ListBookmarkState = { overrides: { [targetKey(FESTIVAL)]: false }, pendingKey: null };

    expect(resolveBookmarkCount(state, FESTIVAL, { bookmarked: true, bookmarkCount: 0 })).toBe(0);
  });
});

describe('createListBookmarkSession', () => {
  it('비로그인은 요청을 보내지 않고 로그인 화면으로 보낸다', async () => {
    // 요청을 보내면 서버가 401을 주고 apiClient가 화면째로 리다이렉트해 스크롤 위치를 잃는다.
    const setBookmark = vi.fn();
    const { controls, goToLogin } = session(false, setBookmark);

    await controls.toggle(FESTIVAL, false);

    expect(setBookmark).not.toHaveBeenCalled();
    expect(goToLogin).toHaveBeenCalledOnce();
  });

  it('서버가 돌려준 상태로만 바꾼다(낙관적 갱신 없음)', async () => {
    const setBookmark = vi.fn().mockResolvedValue({ bookmarked: true });
    const { controls, last } = session(true, setBookmark);

    await controls.toggle(FESTIVAL, false);

    expect(setBookmark).toHaveBeenCalledWith(FESTIVAL, true);
    expect(last().overrides[targetKey(FESTIVAL)]).toBe(true);
    expect(last().pendingKey).toBeNull();
  });

  it('찜한 상태에서 누르면 해제를 요청한다', async () => {
    const setBookmark = vi.fn().mockResolvedValue({ bookmarked: false });
    const { controls, last } = session(true, setBookmark);

    await controls.toggle(SPOT, true);

    expect(setBookmark).toHaveBeenCalledWith(SPOT, false);
    expect(last().overrides[targetKey(SPOT)]).toBe(false);
  });

  it('요청 중에는 연타해도 한 번만 보낸다', async () => {
    const pending = deferred<{ bookmarked: boolean }>();
    const setBookmark = vi.fn().mockReturnValue(pending.promise);
    const { controls, last } = session(true, setBookmark);

    const first = controls.toggle(FESTIVAL, false);
    expect(last().pendingKey).toBe(targetKey(FESTIVAL));
    await controls.toggle(FESTIVAL, false);
    await controls.toggle(SPOT, false);

    expect(setBookmark).toHaveBeenCalledOnce();
    pending.resolve({ bookmarked: true });
    await first;
    expect(last().pendingKey).toBeNull();
  });

  it('실패하면 상태를 바꾸지 않고 pending만 푼다', async () => {
    // 하트가 그대로 남는 편이 "눌렸다가 되돌아간" 것보다 덜 혼란스럽다. 목록에는 오류를 띄울
    // 자리도 없다.
    const setBookmark = vi.fn().mockRejectedValue(new Error('network'));
    const { controls, last } = session(true, setBookmark);

    await controls.toggle(FESTIVAL, false);

    expect(last().overrides).toEqual({});
    expect(last().pendingKey).toBeNull();
  });

  it('세션이 다시 만들어져도 사용자가 바꾼 찜은 이어받는다', async () => {
    // StrictMode는 mount → unmount → mount로 effect를 두 번 돌린다. 이어받지 않으면 두 번째
    // 세션이 빈 overrides로 시작해, 다음 토글이 이전 결과를 지운다.
    const setBookmark = vi.fn().mockResolvedValue({ bookmarked: true });
    const states: ListBookmarkState[] = [];
    const restarted = createListBookmarkSession(
      () => true,
      (state) => states.push(state),
      { setBookmark, goToLogin: vi.fn() },
      { overrides: { [targetKey(SPOT)]: true }, pendingKey: targetKey(SPOT) },
    );

    await restarted.toggle(FESTIVAL, false);

    const last = states[states.length - 1];
    expect(last.overrides[targetKey(SPOT)]).toBe(true);
    expect(last.overrides[targetKey(FESTIVAL)]).toBe(true);
  });

  it('세션을 다시 만들 때 진행 중이던 요청 표시는 이어받지 않는다', async () => {
    // 그 요청은 이전 세션과 함께 버려졌다. 이어받으면 버튼이 영원히 비활성으로 남는다.
    const setBookmark = vi.fn().mockResolvedValue({ bookmarked: true });
    const states: ListBookmarkState[] = [];
    const restarted = createListBookmarkSession(
      () => true,
      (state) => states.push(state),
      { setBookmark, goToLogin: vi.fn() },
      { overrides: {}, pendingKey: targetKey(FESTIVAL) },
    );

    await restarted.toggle(FESTIVAL, false);

    expect(setBookmark).toHaveBeenCalledOnce();
  });

  it('unmount 뒤 도착한 응답은 상태를 바꾸지 않는다', async () => {
    const pending = deferred<{ bookmarked: boolean }>();
    const setBookmark = vi.fn().mockReturnValue(pending.promise);
    const { controls, states } = session(true, setBookmark);

    const running = controls.toggle(FESTIVAL, false);
    controls.stop();
    pending.resolve({ bookmarked: true });
    await running;

    // stop() 이후로는 publish가 화면에 전달되지 않는다.
    expect(states.filter((state) => state.overrides[targetKey(FESTIVAL)] === true)).toHaveLength(0);
  });
});
