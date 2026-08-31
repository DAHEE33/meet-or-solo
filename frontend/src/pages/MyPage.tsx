import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronRight, Heart, MapPinCheck, HeartHandshake, Pencil, ShieldX, Sparkles, UserCog } from 'lucide-react';
import { memberProfileApi, type MemberProfile } from '../api/memberProfile';
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
import { tourSpots } from '../data/mock/tourSpots';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';

const favoriteSpots = tourSpots.slice(1, 4); // 찜한 관광지 mock

export default function MyPage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [preferenceState, setPreferenceState] = useState<PreferenceState>('LOADING');

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

  return (
    <MobileLayout>
      <PageHeader title="마이페이지" noBack />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
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
              {profile?.nickname ?? '프로필 불러오는 중...'}
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
            to="/matching"
            className="flex flex-col gap-1 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
          >
            <HeartHandshake size={18} className="text-coral" />
            <span className="text-sm font-semibold text-ink/55">준비 중</span>
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

        {/* 찜한 관광지 */}
        <section className="flex flex-col gap-2">
          <h2 className="text-[15px] font-bold text-ink">찜한 관광지</h2>
          <div className="flex flex-col gap-2">
            {favoriteSpots.map((spot) => (
              <Link
                key={spot.id}
                to={`/spots/${spot.id}`}
                className="flex items-center gap-3 rounded-2xl bg-white px-4 py-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
              >
                <Heart size={16} className="shrink-0 fill-coral text-coral" />
                <span className="flex-1 text-[14px] font-medium text-ink">{spot.name}</span>
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
          onClick={() => navigate('/login')}
          className="mt-2 rounded-2xl border border-line bg-white py-3.5 text-[14px] font-semibold text-ink/60 active:bg-sand"
        >
          로그아웃
        </button>
      </main>
    </MobileLayout>
  );
}
