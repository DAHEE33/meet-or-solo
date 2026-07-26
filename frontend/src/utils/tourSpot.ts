import type { TourSpot } from '../types';
import type { TourPlaceListItem, TourPlaceDetail } from '../api/spots';
import type { NearbyTourPlaceItem } from '../api/festivals';

export function mapTourPlaceListItemToTourSpot(item: TourPlaceListItem): TourSpot {
  return {
    id: item.id,
    name: item.title,
    address: item.address ?? '',
    imageUrl: item.imageUrl,
  };
}

export function mapTourPlaceDetailToTourSpot(item: TourPlaceDetail): TourSpot {
  return {
    id: item.id,
    name: item.title,
    address: item.address ?? '',
    imageUrl: item.imageUrl,
  };
}

export function mapNearbyTourPlaceToTourSpot(item: NearbyTourPlaceItem): TourSpot {
  return {
    id: item.id,
    name: item.title,
    address: item.address ?? '',
    imageUrl: item.imageUrl,
    distanceKm: Math.round((item.distanceMeters / 1000) * 10) / 10,
  };
}

/** 1km 미만은 m, 이상은 소수 첫째자리 km로 표시한다. */
export function formatDistanceLabel(distanceMeters: number): string {
  return distanceMeters < 1000
    ? `${Math.round(distanceMeters)}m`
    : `${(distanceMeters / 1000).toFixed(1)}km`;
}

/** 실측 도보 시간이 아니라 직선거리 기준 도보 속도(약 4km/h)로 추정한 값이다. */
export function formatWalkMinutesLabel(distanceMeters: number): string {
  const minutes = Math.max(1, Math.round(distanceMeters / 67));
  return `도보 ${minutes}분`;
}
