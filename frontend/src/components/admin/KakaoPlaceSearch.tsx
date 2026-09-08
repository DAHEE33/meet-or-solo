import { useState } from 'react';
import { Search } from 'lucide-react';
import { loadKakaoMaps, type KakaoPlaceSearchItem } from '../matching/KakaoMeetingPointMap';

// 만남 장소 등록/수정 폼에서 위도·경도를 직접 입력하지 않고, 카카오맵 장소 검색으로
// 이름/주소/좌표/kakaoPlaceId를 한 번에 채워 넣기 위한 검색 상자.
// Kakao Local API(REST, 서버용)가 아니라 Maps JS SDK의 services 라이브러리(Places)를 쓴다 —
// 이미 로드하는 VITE_KAKAO_MAPS_APP_KEY 하나로 되고 백엔드가 필요 없다.

export type KakaoPlacePick = {
  name: string;
  address: string;
  longitude: number;
  latitude: number;
  kakaoPlaceId: string;
};

type SearchStatus = 'IDLE' | 'SEARCHING' | 'READY' | 'EMPTY' | 'ERROR';

/** Places 검색 결과 한 건을 폼이 쓰는 형태로 바꾼다. 도로명주소가 없으면 지번주소로 대체하고,
 * 문자열로 오는 좌표(x=경도, y=위도)를 숫자로 바꾼다. */
export function toPlacePick(item: KakaoPlaceSearchItem): KakaoPlacePick {
  return {
    name: item.place_name,
    address: item.road_address_name || item.address_name,
    longitude: Number(item.x),
    latitude: Number(item.y),
    kakaoPlaceId: item.id,
  };
}

export default function KakaoPlaceSearch({ onPick }: { onPick: (place: KakaoPlacePick) => void }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<KakaoPlaceSearchItem[]>([]);
  const [status, setStatus] = useState<SearchStatus>('IDLE');

  async function search() {
    const trimmed = query.trim();
    if (!trimmed) return;
    const appKey = import.meta.env.VITE_KAKAO_MAPS_APP_KEY;
    if (!appKey) {
      setStatus('ERROR');
      return;
    }
    setStatus('SEARCHING');
    try {
      const maps = await loadKakaoMaps(appKey);
      new maps.services.Places().keywordSearch(trimmed, (data, requestStatus) => {
        if (requestStatus !== maps.services.Status.OK) {
          setResults([]);
          setStatus(requestStatus === maps.services.Status.ZERO_RESULT ? 'EMPTY' : 'ERROR');
          return;
        }
        setResults(data);
        setStatus('READY');
      });
    } catch {
      setStatus('ERROR');
    }
  }

  function pick(item: KakaoPlaceSearchItem) {
    onPick(toPlacePick(item));
    setResults([]);
    setStatus('IDLE');
    setQuery(item.place_name);
  }

  return (
    <div>
      <div className="flex gap-2">
        <input
          aria-label="카카오맵 장소 검색"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') { event.preventDefault(); void search(); }
          }}
          placeholder="장소명 또는 주소로 검색"
          className="flex-1 rounded-xl border border-line p-2 font-normal"
        />
        <button
          type="button"
          onClick={() => void search()}
          aria-label="장소 검색"
          className="flex shrink-0 items-center justify-center rounded-xl border border-line px-3"
        >
          <Search size={16} />
        </button>
      </div>
      {status === 'SEARCHING' && <p className="mt-1 text-xs text-ink/45">검색 중...</p>}
      {status === 'EMPTY' && (
        <p className="mt-1 text-xs text-ink/45">검색 결과가 없어요. 아래 필드에 직접 입력해주세요.</p>
      )}
      {status === 'ERROR' && (
        <p className="mt-1 text-xs text-coral">카카오맵 검색을 쓸 수 없어요. 아래 필드에 직접 입력해주세요.</p>
      )}
      {status === 'READY' && results.length > 0 && (
        <ul className="mt-1 flex max-h-40 flex-col gap-0.5 overflow-y-auto rounded-xl border border-line p-1">
          {results.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => pick(item)}
                className="flex w-full flex-col items-start rounded-lg px-2 py-1.5 text-left font-normal hover:bg-sand"
              >
                <span className="text-sm font-semibold">{item.place_name}</span>
                <span className="text-xs text-ink/50">{item.road_address_name || item.address_name}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
