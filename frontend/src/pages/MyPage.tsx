import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronRight, Heart, MapPinCheck, HeartHandshake, Pencil, ShieldX, Sparkles, UserCog } from 'lucide-react';
import { authApi } from '../api/auth';
import { matchHistoryApi } from '../api/matchHistory';
import { memberProfileApi, type MemberProfile } from '../api/memberProfile';
import AccountRestrictionNotice from '../components/common/AccountRestrictionNotice';
import WithdrawalConfirmDialog from '../components/member/WithdrawalConfirmDialog';
import { adminReportsApi } from '../api/adminReports';
import { preferenceEmbeddingApi } from '../api/preferenceEmbedding';
import {
  isPreferenceStateKnown,
  preferenceActionLabel,
  preferenceStatusDescription,
  preferenceStatusLabel,
  preferenceStatusTone,
  resolvePreferenceState,
  type PreferenceState,
} from '../components/preference/preferenceStatus';
import { checkInRecords } from '../data/mock/checkIns';
import { contentBookmarksApi, type BookmarkedContent } from '../api/contentBookmarks';
import { bookmarkedContentId, bookmarkedContentTitle } from '../hooks/useContentBookmark';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import Spinner from '../components/common/Spinner';

/** 마이페이지 요약에 보여줄 찜 항목 수. 전체는 /mypage/favorites에서 본다. */
const FAVORITE_PREVIEW_LIMIT = 6;

/** 요약 카드가 열어야 할 상세 경로. 축제/관광지 중 채워진 쪽을 따른다. */
export function bookmarkedContentPath(item: BookmarkedContent): string | null {
  const id = bookmarkedContentId(item);
  if (id === null) return null;
  return item.targetType === 'FESTIVAL' ? `/festivals/${id}` : `/spots/${id}`;
}

/**
 * 축제와 관광지 찜을 최신순으로 합쳐 상위 N건만 남긴다.
 * 두 종류를 각각 조회하므로 화면에서 한 번 더 정렬해야 순서가 섞이지 않는다.
 */
export function mergeFavoritePreview(
  festivals: readonly BookmarkedContent[],
  tourPlaces: readonly BookmarkedContent[],
  limit = FAVORITE_PREVIEW_LIMIT,
): BookmarkedContent[] {
  return [...festivals, ...tourPlaces]
    .filter((item) => bookmarkedContentPath(item) !== null)
    .sort((left, right) => right.bookmarkedAt.localeCompare(left.bookmarkedAt))
    .slice(0, limit);
}

export default function MyPage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [preferenceState, setPreferenceState] = useState<PreferenceState>('LOADING');
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isWithdrawalOpen, setIsWithdrawalOpen] = useState(false);
  const [isWithdrawing, setIsWithdrawing] = useState(false);
  const [withdrawalError, setWithdrawalError] = useState<string | null>(null);
  const [matchHistory, setMatchHistory] = useState<{ count: number; hasMore: boolean } | null>(null);
  const [favorites, setFavorites] = useState<BookmarkedContent[] | null>(null);

  // 찜 요약은 부가 정보다. 조회에 실패하면 빈 목록으로 두고 마이페이지는 그대로 보여준다.
  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      contentBookmarksApi.getMine('FESTIVAL', 0, FAVORITE_PREVIEW_LIMIT, controller.signal),
      contentBookmarksApi.getMine('TOUR_PLACE', 0, FAVORITE_PREVIEW_LIMIT, controller.signal),
    ])
      .then(([festivalPage, tourPlacePage]) => {
        if (controller.signal.aborted) return;
        setFavorites(mergeFavoritePreview(festivalPage.items, tourPlacePage.items));
      })
      .catch(() => {
        if (!controller.signal.aborted) setFavorites([]);
      });
    return () => controller.abort();
  }, []);

  // 취향 상태는 부가 정보다. 조회에 실패하면 섹션을 조용히 숨기고 마이페이지는 그대로 보여준다.
  useEffect(() => {
    let cancelled = false;
    preferenceEmbeddingApi.get()
      .then((embedding) => {
        if (!cancelled) setPreferenceState(resolvePreferenceState(embedding));
      })
      .catch(() => {
        if (!cancelled) setPreferenceState('UNAVAILABLE');
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    memberProfileApi
      .getMine()
      .then((memberProfile) => {
        if (!cancelled) setProfile(memberProfile);
      })
      .catch(() => {
        if (!cancelled) setErrorMessage('프로필 정보를 불러오지 못했습니다.');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    adminReportsApi.getSession(controller.signal)
      .then(() => { if (!controller.signal.aborted) setIsAdmin(true); })
      .catch(() => { if (!controller.signal.aborted) setIsAdmin(false); });
    return () => controller.abort();
  }, []);

  // 매칭 기록 건수는 부가 정보다. 조회에 실패해도 카드는 그대로 두고 진입만 유지한다.
  useEffect(() => {
    const controller = new AbortController();
    matchHistoryApi.getMine(null, controller.signal)
      .then((history) => {
        if (!controller.signal.aborted) {
          // 첫 page만 읽으므로 뒤가 더 있으면 정확한 총계가 아니라 "20+"로 표기한다.
          setMatchHistory({
            count: history.items.length,
            hasMore: history.pagination.hasNext,
          });
        }
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, []);

  // 로그아웃은 서버가 refresh token을 폐기하고 cookie를 만료시켜야 완료된다.
  // 호출이 실패해도 공용 기기에 화면을 남기지 않도록 로그인으로 보내되 실패 사실은 알린다.
  const handleLogout = async () => {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    try {
      await authApi.logout();
    } catch {
      setErrorMessage('로그아웃 처리에 실패했습니다. 공용 기기라면 브라우저를 종료해 주세요.');
    } finally {
      setIsLoggingOut(false);
      navigate('/login', { replace: true });
    }
  };

  /**
   * 탈퇴는 서버가 익명화와 세션 폐기를 끝내야 완료된다.
   * 실패하면 계정이 그대로 남으므로 dialog를 닫지 않고 사유를 알린다.
   */
  const handleWithdraw = async () => {
    if (isWithdrawing) return;
    setIsWithdrawing(true);
    setWithdrawalError(null);
    try {
      await memberProfileApi.withdraw();
      navigate('/login', { replace: true });
    } catch {
      setWithdrawalError('탈퇴 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setIsWithdrawing(false);
    }
  };

  return (
    <MobileLayout>
      <PageHeader title="마이페이지" noBack />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        {/* 제재 중이면 사유와 기간을 먼저 보여준다. 상태 이름만으로는 이유와 남은 기간을 알 수 없다. */}
        {profile?.sanction && (
          <AccountRestrictionNotice notice={profile.sanction} prominent />
        )}

        {/* 프로필 카드 */}
        <section className="relative flex items-center gap-4 rounded-3xl bg-white p-5 pr-12 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <Link
            to="/profile/edit"
            aria-label="프로필 수정"
            className="absolute right-4 top-4 flex h-8 w-8 items-center justify-center rounded-full bg-sand text-ink/55 active:bg-coral/10 active:text-coral"
          >
            <Pencil size={15} />
          </Link>
          {profile?.profileImageUrl ? (
            <img
              src={profile.profileImageUrl}
              alt={`${profile.nickname} 프로필`}
              className="h-14 w-14 shrink-0 rounded-full object-cover"
            />
          ) : (
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-coral/10 text-lg font-bold text-coral">
              {profile?.nickname?.slice(0, 1) ?? '?'}
            </div>
          )}
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="text-[17px] font-bold text-ink">
              {profile?.nickname ?? <Spinner size="sm" />}
            </span>
            <span className="text-[13px] text-ink/50">
              {profile?.email ?? '등록된 이메일이 없습니다.'}
            </span>
            <span className="mt-1 text-[13px] leading-5 text-ink/65">
              {profile?.intro ?? '아직 작성한 한 줄 소개가 없습니다.'}
            </span>
          </div>
        </section>

        {/* 나의 여행 스타일 */}
        <section className="flex flex-col gap-2">
          <h2 className="text-[15px] font-bold text-ink">나의 여행 스타일</h2>
          <div className="flex flex-wrap gap-1.5">
            {profile?.travelStyles.map((style) => (
              <span key={style.code} className="rounded-full bg-white px-3 py-1.5 text-[13px] text-ink/70 shadow-sm">
                #{style.label}
              </span>
            ))}
            {profile && profile.travelStyles.length === 0 && (
              <span className="text-[13px] text-ink/50">설정된 여행 스타일이 없습니다.</span>
            )}
          </div>
        </section>

        {/* 취향 전격 분석 상태 */}
        {isPreferenceStateKnown(preferenceState) && (
          <section className="flex flex-col gap-2">
            <h2 className="text-[15px] font-bold text-ink">취향 전격 분석</h2>
            <Link
              to="/profile/edit"
              className="flex items-center gap-3 rounded-2xl bg-white px-4 py-3.5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
            >
              <Sparkles size={18} className="shrink-0 text-coral" aria-hidden="true" />
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span
                  className={`w-fit rounded-full px-2.5 py-1 text-[12px] font-semibold ${preferenceStatusTone(preferenceState)}`}
                >
                  {preferenceStatusLabel(preferenceState)}
                </span>
                <span className="text-[13px] leading-5 text-ink/55">
                  {preferenceStatusDescription(preferenceState)}
                </span>
              </span>
              <span className="shrink-0 text-[13px] font-semibold text-coral">
                {preferenceActionLabel(preferenceState)}
              </span>
              <ChevronRight size={16} className="shrink-0 text-ink/30" aria-hidden="true" />
            </Link>
          </section>
        )}

        {errorMessage && <p role="alert" className="text-sm text-coral">{errorMessage}</p>}

        {/* 기록 요약 */}
        <section className="grid grid-cols-2 gap-3">
          <Link
            to="/mypage/matches"
            className="flex flex-col gap-1 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
          >
            <HeartHandshake size={18} className="text-coral" />
            <span className="text-lg font-bold text-ink tabular-nums">
              {matchHistory ? `${matchHistory.count}${matchHistory.hasMore ? '+' : ''}` : '-'}
            </span>
            <span className="text-xs text-ink/50">매칭 기록</span>
          </Link>
          <Link
            to="/check-in"
            className="flex flex-col gap-1 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
          >
            <MapPinCheck size={18} className="text-teal" />
            <span className="text-lg font-bold text-ink tabular-nums">{checkInRecords.length}</span>
            <span className="text-xs text-ink/50">체크인 기록</span>
          </Link>
        </section>

        {/* 찜한 곳 — 축제와 관광지를 함께 최신순으로 보여주고 전체는 /mypage/favorites에서 본다 */}
        <section className="flex flex-col gap-2">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-[15px] font-bold text-ink">찜한 곳</h2>
            <Link to="/mypage/favorites" className="flex items-center text-[13px] font-semibold text-ink/50">
              전체 보기
              <ChevronRight size={14} aria-hidden="true" />
            </Link>
          </div>
          <div className="flex flex-col gap-2">
            {favorites === null && (
              <div className="flex items-center gap-2 rounded-2xl bg-white px-4 py-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
                <Spinner size="sm" />
                <span className="text-[13px] text-ink/50">찜한 곳을 불러오는 중이에요</span>
              </div>
            )}
            {favorites?.length === 0 && (
              <p className="rounded-2xl bg-white px-4 py-3 text-[13px] text-ink/55 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
                아직 찜한 곳이 없어요
              </p>
            )}
            {favorites?.map((item) => (
              <Link
                key={`${item.targetType}-${bookmarkedContentId(item)}`}
                to={bookmarkedContentPath(item) ?? '/mypage/favorites'}
                className="flex items-center gap-3 rounded-2xl bg-white px-4 py-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
              >
                <Heart size={16} className="shrink-0 fill-coral text-coral" />
                <span className="flex-1 truncate text-[14px] font-medium text-ink">
                  {bookmarkedContentTitle(item)}
                </span>
                <ChevronRight size={16} className="text-ink/30" />
              </Link>
            ))}
          </div>
        </section>

        <Link to="/mypage/blocks" className="flex items-center gap-3 rounded-2xl bg-white px-4 py-3 shadow-sm">
          <ShieldX size={18} className="text-coral" aria-hidden="true" />
          <span className="flex-1 text-[14px] font-semibold text-ink">차단 회원 관리</span>
          <ChevronRight size={16} className="text-ink/30" aria-hidden="true" />
        </Link>

        {isAdmin && <Link to="/admin" className="flex items-center gap-3 rounded-2xl bg-white px-4 py-3 shadow-sm">
          <UserCog size={18} className="text-teal" aria-hidden="true" />
          <span className="flex-1 text-[14px] font-semibold text-ink">관리자 기능</span>
          <ChevronRight size={16} className="text-ink/30" aria-hidden="true" />
        </Link>}

        <button
          type="button"
          onClick={handleLogout}
          disabled={isLoggingOut}
          className="mt-2 rounded-2xl border border-line bg-white py-3.5 text-[14px] font-semibold text-ink/60 active:bg-sand disabled:opacity-60"
        >
          {isLoggingOut ? '로그아웃 중...' : '로그아웃'}
        </button>

        {/* 탈퇴는 되돌릴 수 없어 로그아웃보다 약하게 두고, 실행은 확인 dialog를 거친다. */}
        <button
          type="button"
          onClick={() => { setWithdrawalError(null); setIsWithdrawalOpen(true); }}
          className="pb-2 text-[13px] font-semibold text-ink/40 underline"
        >
          회원 탈퇴
        </button>
      </main>

      {isWithdrawalOpen && (
        <WithdrawalConfirmDialog
          sanction={profile?.sanction ?? null}
          submitting={isWithdrawing}
          errorMessage={withdrawalError}
          onClose={() => setIsWithdrawalOpen(false)}
          onConfirm={handleWithdraw}
        />
      )}
    </MobileLayout>
  );
}
