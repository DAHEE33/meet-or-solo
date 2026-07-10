import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  memberProfileApi,
  type AgeRange,
  type Gender,
  type TravelStyleCode,
} from '../api/memberProfile';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import Chip from '../components/common/Chip';

const TRAVEL_STYLES: { code: TravelStyleCode; label: string }[] = [
  { code: 'RELAXED', label: '느긋하게' },
  { code: 'ACTIVE', label: '액티브' },
  { code: 'FOOD', label: '맛집탐방' },
  { code: 'PHOTO', label: '사진위주' },
  { code: 'CULTURE', label: '문화답사' },
];
const GENDERS: { value: Gender; label: string }[] = [
  { value: 'FEMALE', label: '여성' },
  { value: 'MALE', label: '남성' },
  { value: 'OTHER', label: '기타/선택 안 함' },
];
const AGE_RANGES: { value: AgeRange; label: string }[] = [
  { value: '10S', label: '10대' },
  { value: '20S', label: '20대' },
  { value: '30S', label: '30대' },
  { value: '40S', label: '40대' },
  { value: '50S', label: '50대' },
  { value: '60_PLUS', label: '60대 이상' },
];

export default function SignupPage() {
  const navigate = useNavigate();
  const [nickname, setNickname] = useState('');
  const [gender, setGender] = useState<Gender | ''>('');
  const [ageRange, setAgeRange] = useState<AgeRange | ''>('');
  const [styles, setStyles] = useState<TravelStyleCode[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    memberProfileApi
      .getMine()
      .then((profile) => {
        if (cancelled) return;
        if (profile.status === 'ACTIVE') {
          navigate('/', { replace: true });
          return;
        }
        setNickname(profile.nickname ?? '');
        setGender(profile.gender ?? '');
        setAgeRange(profile.ageRange ?? '');
        setStyles(profile.travelStyles.map((style) => style.code));
        setIsLoading(false);
      })
      .catch(() => {
        if (!cancelled) navigate('/login', { replace: true });
      });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const toggleStyle = (style: TravelStyleCode) => {
    setErrorMessage(null);
    setStyles((prev) => {
      if (prev.includes(style)) return prev.filter((selected) => selected !== style);
      if (prev.length >= 3) {
        setErrorMessage('여행 스타일은 최대 3개까지 선택할 수 있습니다.');
        return prev;
      }
      return [...prev, style];
    });
  };

  const handleComplete = async () => {
    if (!nickname.trim() || !gender || !ageRange || styles.length === 0) {
      setErrorMessage('닉네임, 성별, 연령대, 여행 스타일을 모두 입력해 주세요.');
      return;
    }

    setIsSaving(true);
    setErrorMessage(null);
    try {
      const profile = await memberProfileApi.complete({
        nickname: nickname.trim(),
        gender,
        ageRange,
        travelStyles: styles,
      });
      if (profile.status === 'ACTIVE') {
        navigate('/', { replace: true });
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '프로필 저장에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  const inputClass =
    'rounded-2xl border border-line bg-white px-4 py-3.5 text-[15px] text-ink outline-none placeholder:text-ink/35 focus:border-coral';

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader title="나의 프로필 설정" />
      <main className="flex flex-col gap-6 px-5 pb-10 pt-2">
        {isLoading ? (
          <p className="py-10 text-center text-sm text-ink/50">프로필을 불러오는 중...</p>
        ) : (
          <>
        <div className="flex flex-col gap-3">
          <label htmlFor="profile-nickname" className="text-[15px] font-bold text-ink">
            닉네임
          </label>
          <input
            id="profile-nickname"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="닉네임"
            maxLength={50}
            className={inputClass}
          />
        </div>

        <section className="flex flex-col gap-3">
          <h2 className="text-[17px] font-bold text-ink">성별</h2>
          <div className="grid grid-cols-3 gap-2">
            {GENDERS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setGender(option.value)}
                className={`rounded-2xl border px-2 py-3 text-sm font-semibold ${
                  gender === option.value
                    ? 'border-coral bg-coral/10 text-coral'
                    : 'border-line bg-white text-ink/60'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </section>

        <section className="flex flex-col gap-3">
          <label htmlFor="profile-age-range" className="text-[17px] font-bold text-ink">
            연령대
          </label>
          <select
            id="profile-age-range"
            value={ageRange}
            onChange={(e) => setAgeRange(e.target.value as AgeRange | '')}
            className={inputClass}
          >
            <option value="">연령대를 선택해 주세요</option>
            {AGE_RANGES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="text-[17px] font-bold text-ink">나의 여행 스타일</h2>
          <p className="-mt-2 text-[13px] text-ink/50">매칭 추천에 사용돼요. 1~3개 선택해 주세요.</p>
          <div className="flex flex-wrap gap-2">
            {TRAVEL_STYLES.map((style) => (
              <Chip
                key={style.code}
                label={style.label}
                selected={styles.includes(style.code)}
                onClick={() => toggleStyle(style.code)}
              />
            ))}
          </div>
        </section>

        {errorMessage && (
          <p role="alert" className="rounded-2xl bg-coral/10 px-4 py-3 text-sm text-coral">
            {errorMessage}
          </p>
        )}

        <PrimaryButton onClick={handleComplete} disabled={isSaving}>
          {isSaving ? '저장 중...' : '프로필 설정 완료'}
        </PrimaryButton>
          </>
        )}
      </main>
    </MobileLayout>
  );
}
