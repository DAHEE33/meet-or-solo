import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import EngagementCounts from './EngagementCounts';
import FestivalListItem from '../festival/FestivalListItem';
import ExploreSpotItem from '../explore/ExploreSpotItem';
import FestivalNearbyPlaceItem from '../home/FestivalNearbyPlaceItem';
import type { Festival, TourSpot } from '../../types';

const render = (element: React.ReactElement) =>
  renderToStaticMarkup(<MemoryRouter>{element}</MemoryRouter>);

const festival = (overrides: Partial<Festival> = {}): Festival => ({
  id: 298,
  name: '춘천 마임축제',
  status: 'ongoing',
  ddayLabel: '',
  periodShort: '8.1 – 8.5',
  periodFull: '2026.08.01 – 2026.08.05',
  address: '강원특별자치도 춘천시',
  intro: '',
  thumbnailUrl: null,
  infoItems: [],
  programs: [],
  nearbyPlaces: [],
  ...overrides,
});

const spot = (overrides: Partial<TourSpot> = {}): TourSpot => ({
  id: 7,
  name: '소양강 스카이워크',
  contentTypeId: '12',
  address: '강원특별자치도 춘천시',
  imageUrl: null,
  ...overrides,
});

describe('EngagementCounts', () => {
  it('좋아요 수와 후기 수를 스크린리더가 읽을 수 있는 라벨과 함께 보여준다', () => {
    const html = render(<EngagementCounts bookmarkCount={12} commentCount={3} />);

    expect(html).toContain('12');
    expect(html).toContain('3');
    expect(html).toContain('aria-label="좋아요 12개"');
    expect(html).toContain('aria-label="후기 3개"');
  });

  it('0건도 감추지 않고 그대로 보여준다', () => {
    // 숨기면 "아직 아무도 안 눌렀다"와 "집계가 안 왔다"를 화면에서 구분할 수 없다.
    const html = render(<EngagementCounts bookmarkCount={0} commentCount={0} />);

    expect(html).toContain('aria-label="좋아요 0개"');
    expect(html).toContain('aria-label="후기 0개"');
  });
});

describe('목록 카드의 좋아요·후기 수', () => {
  it('축제 카드 오른쪽에 두 수를 함께 그린다', () => {
    const html = render(
      <FestivalListItem festival={festival({ bookmarkCount: 12, commentCount: 3 })} />,
    );

    expect(html).toContain('aria-label="좋아요 12개"');
    expect(html).toContain('aria-label="후기 3개"');
  });

  it('관광지 카드 오른쪽에 두 수를 함께 그린다', () => {
    const html = render(<ExploreSpotItem spot={spot({ bookmarkCount: 5, commentCount: 1 })} />);

    expect(html).toContain('aria-label="좋아요 5개"');
    expect(html).toContain('aria-label="후기 1개"');
  });

  it('핸들러를 주면 하트가 누를 수 있는 버튼이 된다', () => {
    const html = render(
      <EngagementCounts bookmarkCount={4} commentCount={1} onToggleBookmark={() => {}} />,
    );

    expect(html).toContain('<button');
    expect(html).toContain('aria-label="찜하기"');
    expect(html).toContain('aria-pressed="false"');
  });

  it('이미 찜한 항목은 눌린 상태로 표시하고 해제 라벨을 준다', () => {
    const html = render(
      <EngagementCounts bookmarkCount={4} commentCount={1} bookmarked onToggleBookmark={() => {}} />,
    );

    expect(html).toContain('aria-label="찜 해제"');
    expect(html).toContain('aria-pressed="true"');
    expect(html).toContain('fill-coral');
  });

  it('요청 중에는 버튼을 비활성화해 연타를 막는다', () => {
    const html = render(
      <EngagementCounts bookmarkCount={4} commentCount={1} onToggleBookmark={() => {}} pending />,
    );

    expect(html).toContain('disabled');
  });

  it('핸들러가 없으면 버튼이 아니라 표시 전용이다', () => {
    // 찜 목록처럼 토글이 화면의 전제를 무너뜨리는 곳이 있어 기본은 표시 전용이다.
    const html = render(<EngagementCounts bookmarkCount={4} commentCount={1} />);

    expect(html).not.toContain('<button');
    expect(html).toContain('aria-label="좋아요 4개"');
  });

  it('카드의 하트는 링크 안에 있지 않다', () => {
    // 링크 안에 있으면 하트를 눌러도 상세 화면으로 이동해 버린다.
    const html = render(
      <FestivalListItem
        festival={festival({ bookmarkCount: 1, commentCount: 0 })}
        onToggleBookmark={() => {}}
      />,
    );

    const linkEnd = html.indexOf('</a>');
    expect(linkEnd).toBeGreaterThan(-1);
    expect(html.indexOf('<button')).toBeGreaterThan(linkEnd);
  });

  it('홈 "축제와 함께 둘러보기" 카드도 두 수를 그리고 하트는 링크 바깥에 둔다', () => {
    const html = render(
      <FestivalNearbyPlaceItem
        spot={spot({ bookmarkCount: 3, commentCount: 2 })}
        distanceLabel="200m"
        walkLabel="도보 3분"
        onToggleBookmark={() => {}}
      />,
    );

    expect(html).toContain('aria-label="찜하기"');
    expect(html).toContain('aria-label="후기 2개"');
    expect(html.indexOf('<button')).toBeGreaterThan(html.indexOf('</a>'));
  });

  it('집계가 없는 경로(찜 목록 이전 응답, 상세 매퍼)에서는 아예 그리지 않는다', () => {
    // optional 필드라 undefined가 0으로 보이면 안 된다.
    const festivalHtml = render(<FestivalListItem festival={festival()} />);
    const spotHtml = render(<ExploreSpotItem spot={spot()} />);

    expect(festivalHtml).not.toContain('aria-label="좋아요');
    expect(spotHtml).not.toContain('aria-label="좋아요');
  });
});
