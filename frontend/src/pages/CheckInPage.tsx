import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import { festivalsApi, type FestivalDetail } from '../api/festivals';
import { readNumberFromLocationState } from '../utils/positiveInteger';
import { useFestivalCheckin } from '../hooks/useFestivalCheckin';
import { formatDistanceLabel } from '../utils/tourSpot';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import GPSPermissionModal from '../components/common/GPSPermissionModal';

/**
 * 매칭 신청 중 "체크인이 필요해요" 안내를 통해 festivalId를 route state로 넘겨받아 오는 화면.
 * festivalId 없이 이 화면에 직접 들어오면(새로고침, 직접 URL 접근 등) 어떤 축제인지 알 수 없으므로
 * 축제를 먼저 고르라는 안내만 보여준다.
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

  if (festivalId === null) {
    return (
      <MobileLayout showTabBar={false}>
        <PageHeader title="체크인" />
        <main className="flex flex-col items-center gap-3 px-5 py-16 text-center">
          <p className="text-[14px] text-ink/55">먼저 체크인할 축제를 골라주세요.</p>
          <Link to="/spots" className="text-[13px] font-semibold text-coral">
            축제 목록 보러가기
          </Link>
        </main>
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
            {festivalLoading ? '축제 정보를 불러오는 중...' : festival?.title ?? '축제 정보를 불러올 수 없어요'}
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
