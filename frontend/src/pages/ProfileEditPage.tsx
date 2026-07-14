import { useEffect, useState, type ChangeEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  memberProfileApi,
  type AgeRange,
  type Gender,
  type TravelStyleCode,
} from '../api/memberProfile';
import Chip from '../components/common/Chip';
import PrimaryButton from '../components/common/PrimaryButton';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import { NICKNAME_MAX_LENGTH, NICKNAME_RULE_MESSAGE, validateNickname } from '../utils/nickname';

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

export default function ProfileEditPage() {
  const navigate = useNavigate();
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [intro, setIntro] = useState('');
  const [gender, setGender] = useState<Gender | ''>('');
  const [ageRange, setAgeRange] = useState<AgeRange | ''>('');
  const [styles, setStyles] = useState<TravelStyleCode[]>([]);
  const [profileImageUrl, setProfileImageUrl] = useState<string | null>(null);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    memberProfileApi.getMine().then((profile) => {
      if (cancelled) return;
      setNickname(profile.nickname ?? '');
      setEmail(profile.email ?? '');
      setIntro(profile.intro ?? '');
      setGender(profile.gender ?? '');
      setAgeRange(profile.ageRange ?? '');
      setStyles(profile.travelStyles.map((style) => style.code));
      setProfileImageUrl(profile.profileImageUrl);
      setIsLoading(false);
    }).catch(() => {
      if (!cancelled) {
        setErrorMessage('프로필 정보를 불러오지 못했습니다.');
        setIsLoading(false);
      }
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => () => {
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl);
  }, [imagePreviewUrl]);

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    setErrorMessage(null);
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setErrorMessage('JPEG, PNG, WEBP 이미지만 선택할 수 있습니다.');
      event.target.value = '';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setErrorMessage('프로필 이미지는 5MB 이하만 업로드할 수 있습니다.');
      event.target.value = '';
      return;
    }
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl);
    setImageFile(file);
    setImagePreviewUrl(URL.createObjectURL(file));
  };

  const toggleStyle = (style: TravelStyleCode) => {
    setErrorMessage(null);
    setStyles((current) => {
      if (current.includes(style)) return current.filter((item) => item !== style);
      if (current.length >= 3) {
        setErrorMessage('여행 스타일은 최대 3개까지 선택할 수 있습니다.');
        return current;
      }
      return [...current, style];
    });
  };

  const handleSave = async () => {
    const nicknameError = validateNickname(nickname);
    if (nicknameError) {
      setErrorMessage(nicknameError);
      return;
    }
    if (!gender || !ageRange || styles.length === 0) {
      setErrorMessage('닉네임, 성별, 연령대, 여행 스타일을 모두 입력해 주세요.');
      return;
    }
    setIsSaving(true);
    setErrorMessage(null);
    try {
      await memberProfileApi.complete({
        nickname: nickname.trim(),
        email: email.trim() || null,
        intro: intro.trim() || null,
        gender,
        ageRange,
        travelStyles: styles,
      });
      if (imageFile) await memberProfileApi.uploadImage(imageFile);
      navigate('/mypage', { replace: true });
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
      <PageHeader title="프로필 수정" />
      <main className="flex flex-col gap-6 px-5 pb-10 pt-2">
        {isLoading ? <p className="py-10 text-center text-sm text-ink/50">프로필을 불러오는 중...</p> : <>
          <section className="flex flex-col items-center gap-3">
            {imagePreviewUrl || profileImageUrl ? (
              <img
                src={imagePreviewUrl ?? profileImageUrl ?? undefined}
                alt="프로필 이미지 미리보기"
                className="h-24 w-24 rounded-full object-cover"
              />
            ) : (
              <div className="flex h-24 w-24 items-center justify-center rounded-full bg-coral/10 text-2xl font-bold text-coral">
                {nickname.slice(0, 1) || '?'}
              </div>
            )}
            <label className="cursor-pointer rounded-xl border border-line bg-white px-4 py-2 text-sm font-semibold text-ink/65 active:bg-sand">
              이미지 선택
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleImageChange}
                className="sr-only"
              />
            </label>
            <p className="text-center text-xs text-ink/45">JPEG, PNG, WEBP · 최대 5MB</p>
          </section>
          <label className="flex flex-col gap-2 text-[15px] font-bold text-ink">
            닉네임
            <input
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              maxLength={NICKNAME_MAX_LENGTH}
              className={inputClass}
            />
            <span className="text-xs font-normal text-ink/45">{NICKNAME_RULE_MESSAGE}</span>
          </label>
          <label className="flex flex-col gap-2 text-[15px] font-bold text-ink">
            이메일 <span className="text-xs font-normal text-ink/45">비워 두어도 괜찮아요.</span>
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} maxLength={255} placeholder="등록된 이메일이 없습니다." className={inputClass} />
          </label>
          <label className="flex flex-col gap-2 text-[15px] font-bold text-ink">
            한 줄 소개 <span className="text-xs font-normal text-ink/45">여행 취향을 짧게 소개해 주세요.</span>
            <input value={intro} onChange={(event) => setIntro(event.target.value)} maxLength={160} placeholder="아직 작성한 소개가 없습니다." className={inputClass} />
          </label>
          <section className="flex flex-col gap-3">
            <h2 className="text-[17px] font-bold text-ink">성별</h2>
            <div className="grid grid-cols-3 gap-2">{GENDERS.map((option) => (
              <button key={option.value} type="button" onClick={() => setGender(option.value)} className={`rounded-2xl border px-2 py-3 text-sm font-semibold ${gender === option.value ? 'border-coral bg-coral/10 text-coral' : 'border-line bg-white text-ink/60'}`}>{option.label}</button>
            ))}</div>
          </section>
          <label className="flex flex-col gap-3 text-[17px] font-bold text-ink">
            연령대
            <select value={ageRange} onChange={(event) => setAgeRange(event.target.value as AgeRange | '')} className={inputClass}>
              <option value="">연령대를 선택해 주세요</option>
              {AGE_RANGES.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <section className="flex flex-col gap-3">
            <h2 className="text-[17px] font-bold text-ink">나의 여행 스타일</h2>
            <p className="-mt-2 text-[13px] text-ink/50">1~3개 선택해 주세요.</p>
            <div className="flex flex-wrap gap-2">{TRAVEL_STYLES.map((style) => (
              <Chip key={style.code} label={style.label} selected={styles.includes(style.code)} onClick={() => toggleStyle(style.code)} />
            ))}</div>
          </section>
          {errorMessage && <p role="alert" className="rounded-2xl bg-coral/10 px-4 py-3 text-sm text-coral">{errorMessage}</p>}
          <PrimaryButton onClick={handleSave} disabled={isSaving}>{isSaving ? '저장 중...' : '수정 내용 저장'}</PrimaryButton>
        </>}
      </main>
    </MobileLayout>
  );
}
