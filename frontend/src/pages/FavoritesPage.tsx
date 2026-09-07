import { Heart } from 'lucide-react';
import type { ContentTargetType } from '../api/contentBookmarks';
import {
  bookmarkedContentId,
  bookmarkedContentTitle,
  useBookmarkedContents,
} from '../hooks/useContentBookmark';
import { mapFestivalListItemToFestival } from '../utils/festival';
import { mapTourPlaceListItemToTourSpot } from '../utils/tourSpot';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import Chip from '../components/common/Chip';
import { LoadingState } from '../components/common/Spinner';
import FestivalListItem from '../components/festival/FestivalListItem';
import ExploreSpotItem from '../components/explore/ExploreSpotItem';

const TABS: { type: ContentTargetType; label: string }[] = [
  { type: 'FESTIVAL', label: '축제' },
  { type: 'TOUR_PLACE', label: '관광지' },
];

/**
 * 내 찜 목록. `/mypage/blocks`와 같은 계층이며 BlockedMembersPage의 상태 분기 패턴을 따른다.
 * 항목 카드는 기존 FestivalListItem / ExploreSpotItem을 그대로 재사용한다(docs/27 7.3).
 */
export default function FavoritesPage() {
  const { state, reload, changeType, remove, clearSuccess } = useBookmarkedContents();

  return (
    <MobileLayout>
      <PageHeader title="찜한 곳" />
      <main className="flex flex-col gap-3 px-5 pb-10" aria-busy={state.status === 'LOADING'}>
        <div className="flex gap-2">
          {TABS.map((tab) => (
            <Chip
              key={tab.type}
              label={tab.label}
              selected={state.type === tab.type}
              onClick={() => void changeType(tab.type)}
            />
          ))}
        </div>

        <div className="sr-only" role="status" aria-live="polite">
          {state.status === 'LOADING' ? '찜 목록을 불러오는 중입니다.' : state.successMessage ?? ''}
        </div>

        {state.status === 'LOADING' && (
          <div className="rounded-2xl bg-white p-5">
            <LoadingState className="py-2" message="찜한 곳을 불러오는 중이에요" />
          </div>
        )}

        {state.status === 'ERROR' && (
          <section role="alert" className="rounded-2xl bg-white p-5">
            <p className="text-sm text-coral">찜 목록을 불러오지 못했어요.</p>
            <button
              type="button"
              onClick={() => void reload()}
              className="mt-3 rounded-xl border border-line px-4 py-2 text-sm font-semibold"
            >
              다시 시도
            </button>
          </section>
        )}

        {state.status === 'READY' && state.items.length === 0 && (
          <p className="rounded-2xl bg-white p-5 text-center text-sm text-ink/60">
            아직 찜한 곳이 없어요
          </p>
        )}

        {state.status === 'READY' &&
          state.items.map((item) => {
            const contentId = bookmarkedContentId(item);
            return (
              <div key={`${item.targetType}-${contentId}`} className="flex items-center gap-2">
                <div className="min-w-0 flex-1">
                  {item.festival ? (
                    <FestivalListItem festival={mapFestivalListItemToFestival(item.festival)} />
                  ) : (
                    item.tourPlace && (
                      <ExploreSpotItem spot={mapTourPlaceListItemToTourSpot(item.tourPlace)} />
                    )
                  )}
                </div>
                <button
                  type="button"
                  aria-label={`${bookmarkedContentTitle(item)} 찜 해제`}
                  disabled={state.removingId === contentId}
                  onClick={() => void remove(item)}
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full active:bg-black/5 disabled:opacity-40"
                >
                  <Heart size={20} strokeWidth={1.8} className="fill-coral text-coral" />
                </button>
              </div>
            );
          })}

        {state.error && state.status === 'READY' && (
          <p role="alert" className="text-[13px] text-coral">
            찜을 해제하지 못했어요. 다시 시도해주세요.
          </p>
        )}

        {state.successMessage && (
          <div role="status" aria-live="polite" className="rounded-2xl bg-ink px-4 py-3 text-sm text-white">
            {state.successMessage}
            <button type="button" className="ml-2 underline" onClick={clearSuccess}>
              닫기
            </button>
          </div>
        )}
      </main>
    </MobileLayout>
  );
}
