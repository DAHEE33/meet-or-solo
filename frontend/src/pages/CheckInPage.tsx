import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import { festivalsApi, type FestivalDetail } from '../api/festivals';
import { readNumberFromLocationState } from '../utils/positiveInteger';
import { useFestivalCheckin } from '../hooks/useFestivalCheckin';
import { formatDistanceLabel } from '../utils/tourSpot';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import GPSPermissionModal from '../components/common/GPSPermissionModal';
import Spinner, { LoadingState } from '../components/common/Spinner';

/**
 * 매칭 신청 중 "체크인이 필요해요" 안내를 통해 festivalId를 route state로 넘겨받아 오는 화면.
 * festivalId 없이 이 화면에 직접 들어오면(새로고침, 직접 URL 접근 등) 어떤 축제인지 알 수 없어
 * 축제·관광 탐색(/spots)으로 곧바로 이동시킨다.
 */
export default function CheckInPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const festivalId = readNumberFromLocationState(location.state, 'festivalId');

  const [festival, setFestival] = useState<FestivalDetail | null>(null);
  const [festivalLoading, setFestivalLoading] = useState(festivalId !== null);
  const [showPermissionModal, setShowPermissionModal] = useState(false);
  const { state: checkinState, checkIn } = useFestivalCheckin(festivalId);

  useEffect(() => {
    if (festivalId === null) {
      setFestivalLoading(false);
      return;
    }
    let mounted = true;
    setFestivalLoading(true);
    festivalsApi
      .getDetail(festivalId)
      .then((data) => {
        if (mounted) setFestival(data);
      })
      .catch(() => {
        if (mounted) setFestival(null);
      })
      .finally(() => {
        if (mounted) setFestivalLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [festivalId]);

  // festivalId 없이 들어오면(직접 URL, 새로고침 등) 어느 축제로 체크인할지 알 수 없어 이 화면이
  // 할 수 있는 게 없다. 예전에는 "축제를 골라주세요" 안내를 보여줬는데, 결국 사용자가 축제를
  // 고르러 가야 하므로 축제·관광 탐색으로 바로 보낸다. replace로 이동해 뒤로가기가 이 빈
  // 화면으로 돌아오지 않게 한다.
  useEffect(() => {
    if (festivalId === null) navigate('/spots', { replace: true });
  }, [festivalId, navigate]);

  if (festivalId === null) {
    return (
      <MobileLayout showTabBar={false}>
        <PageHeader title="체크인" />
        <LoadingState message="축제 목록으로 이동하고 있어요" />
      </MobileLayout>
    );
  }

  const busy = checkinState.status === 'locating' || checkinState.status === 'submitting';

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader title="체크인" />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        <section className="flex flex-col items-center gap-3 rounded-3xl bg-white p-6 text-center shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <span className="flex items-center gap-1 text-xs text-ink/50">
            <MapPin size={13} className="text-coral" />
            {festivalLoading ? <Spinner size="sm" /> : festival?.title ?? '축제 정보를 불러올 수 없어요'}
          </span>
          <h2 className="text-xl font-bold text-ink">
            {checkinState.status === 'success' ? '체크인 완료' : '이 축제에 체크인하기'}
          </h2>
          {checkinState.status === 'success' && (
            <p className="text-[13px] text-ink/55">
              현재 위치에서 {formatDistanceLabel(checkinState.result.distanceMeters)} 떨어진 곳에서
              체크인했어요.
            </p>
          )}
          {checkinState.status === 'error' && (
            <p className="text-[13px] text-coral">{checkinState.message}</p>
          )}
          {checkinState.status === 'success' ? (
            <PrimaryButton onClick={() => navigate('/matching', { state: { festivalId } })}>
              매칭 신청하러 가기
            </PrimaryButton>
          ) : (
            <PrimaryButton
              tone="ink"
              onClick={() => setShowPermissionModal(true)}
              disabled={busy || festivalLoading}
              pending={busy}
            >
              {checkinState.status === 'locating'
                ? '위치 확인 중...'
                : checkinState.status === 'submitting'
                  ? '체크인 처리 중...'
                  : '체크인하기'}
            </PrimaryButton>
          )}
        </section>
      </main>
      {showPermissionModal && (
        <GPSPermissionModal
          onConfirm={() => {
            setShowPermissionModal(false);
            void checkIn();
          }}
          onCancel={() => setShowPermissionModal(false)}
        />
      )}
    </MobileLayout>
  );
}
