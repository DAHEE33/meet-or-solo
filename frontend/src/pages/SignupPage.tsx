import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  memberProfileApi,
  type AgeRange,
  type Gender,
  type TravelStyleCode,
} from '../api/memberProfile';
import { agreeAll, SIGNUP_CONSENT_TYPES } from '../api/memberConsents';
import { preferenceEmbeddingApi } from '../api/preferenceEmbedding';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import Chip from '../components/common/Chip';
import AiConsentSection, {
  EMPTY_AI_CONSENT_DRAFT,
  isAiConsentComplete,
  type AiConsentDraft,
} from '../components/consent/AiConsentSection';
import { SIGNUP_PRIVACY_LABEL, SIGNUP_TERMS_LABEL } from '../components/consent/consentNotice';
import PreferenceInputSection from '../components/preference/PreferenceInputSection';
import {
  EMPTY_PREFERENCE_DRAFT,
  PREFERENCE_TEXT_MAX_LENGTH,
  buildPreferenceText,
  isPreferenceDraftComplete,
  type PreferenceDraft,
} from '../components/preference/preferenceText';
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

export default function SignupPage() {
  const navigate = useNavigate();
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState<string | null>(null);
  const [intro, setIntro] = useState<string | null>(null);
  const [gender, setGender] = useState<Gender | ''>('');
  const [ageRange, setAgeRange] = useState<AgeRange | ''>('');
  const [styles, setStyles] = useState<TravelStyleCode[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [agreedTerms, setAgreedTerms] = useState(false);
  const [agreedPrivacy, setAgreedPrivacy] = useState(false);
  const [aiConsent, setAiConsent] = useState<AiConsentDraft>(EMPTY_AI_CONSENT_DRAFT);
  const [prefDraft, setPrefDraft] = useState<PreferenceDraft>(EMPTY_PREFERENCE_DRAFT);
  /** 프로필은 저장됐는데 취향 저장만 실패한 상태. 가입 자체는 이미 끝났다. */
  const [isProfileSaved, setIsProfileSaved] = useState(false);
  const [preferenceNotice, setPreferenceNotice] = useState<string | null>(null);

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
        setEmail(profile.email);
        setIntro(profile.intro);
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

  const hasPreferenceInput =
    prefDraft.activity.trim().length > 0 ||
    prefDraft.companion.trim().length > 0 ||
    prefDraft.free.trim().length > 0;

  /**
   * 취향은 선택 입력이다. 입력했는데 저장하지 못한 경우에만 안내를 남기고, 가입 자체는 이미
   * 끝났으므로 되돌리지 않는다. 임베딩 실패가 가입을 막지 않는다는 기존 원칙과 같다.
   */
  const savePreference = async (): Promise<string | null> => {
    if (!hasPreferenceInput) return null;
    if (!isAiConsentComplete(aiConsent)) {
      return '취향 분석 동의 두 가지를 모두 체크해야 취향을 저장할 수 있어요. 프로필 수정에서 언제든 다시 저장할 수 있어요.';
    }
    if (!isPreferenceDraftComplete(prefDraft)) {
      return '취향 가이드 두 문항을 모두 답해야 저장돼요. 프로필 수정에서 언제든 다시 저장할 수 있어요.';
    }
    const preferenceText = buildPreferenceText(prefDraft);
    if (preferenceText.length > PREFERENCE_TEXT_MAX_LENGTH) {
      return `취향 글은 ${PREFERENCE_TEXT_MAX_LENGTH}자 이하여야 저장돼요. 프로필 수정에서 언제든 다시 저장할 수 있어요.`;
    }

    try {
      await agreeAll(['AI_PROCESSING', 'OVERSEAS_TRANSFER']);
      await preferenceEmbeddingApi.createOrUpdate(preferenceText);
      return null;
    } catch {
      return '취향은 저장하지 못했어요. 프로필 수정에서 다시 저장할 수 있어요.';
    }
  };

  const handleComplete = async () => {
    const nicknameError = validateNickname(nickname);
    if (nicknameError) {
      setErrorMessage(nicknameError);
      return;
    }
    if (!gender || !ageRange || styles.length === 0) {
      setErrorMessage('닉네임, 성별, 연령대, 여행 스타일을 모두 입력해 주세요.');
      return;
    }
    if (!agreedTerms || !agreedPrivacy) {
      setErrorMessage('이용약관과 개인정보 수집·이용에 동의해 주세요.');
      return;
    }

    setIsSaving(true);
    setErrorMessage(null);
    try {
      // 서버가 최초 가입 완료 시점에 약관·개인정보 동의를 요구하므로 프로필보다 먼저 기록한다.
      await agreeAll(SIGNUP_CONSENT_TYPES);
      const profile = await memberProfileApi.complete({
        nickname: nickname.trim(),
        email,
        intro,
        gender,
        ageRange,
        travelStyles: styles,
      });
      if (profile.status !== 'ACTIVE') return;

      setIsProfileSaved(true);
      const notice = await savePreference();
      if (notice) {
        setPreferenceNotice(notice);
        return;
      }
      navigate('/', { replace: true });
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
            maxLength={NICKNAME_MAX_LENGTH}
            className={inputClass}
          />
          <p className="-mt-1 text-xs text-ink/45">{NICKNAME_RULE_MESSAGE}</p>
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

        <section className="flex flex-col gap-3 border-t border-line pt-5">
          <div className="flex flex-col gap-0.5">
            <h2 className="text-[17px] font-bold text-ink">
              취향 전격 분석 <span className="text-[13px] font-normal text-ink/40">(선택)</span>
            </h2>
            <p className="text-[13px] text-ink/50">
              두 문항만 답하면 나와 잘 맞는 사람을 찾아드려요. 나중에 프로필 수정에서 입력해도 돼요.
            </p>
          </div>
          <AiConsentSection value={aiConsent} onChange={setAiConsent} disabled={isSaving} />
          <PreferenceInputSection
            value={prefDraft}
            onChange={(draft) => {
              setPrefDraft(draft);
              setPreferenceNotice(null);
            }}
            title={null}
            disabled={isSaving || !isAiConsentComplete(aiConsent)}
          />
          {!isAiConsentComplete(aiConsent) && (
            <p className="-mt-1 text-[12px] text-ink/40">
              위 두 가지에 동의하면 취향을 입력할 수 있어요.
            </p>
          )}
        </section>

        <section className="flex flex-col gap-2.5 border-t border-line pt-5">
          <label className="flex items-start gap-2.5">
            <input
              type="checkbox"
              checked={agreedTerms}
              disabled={isSaving}
              onChange={(e) => {
                setAgreedTerms(e.target.checked);
                setErrorMessage(null);
              }}
              className="mt-0.5 h-5 w-5 shrink-0 accent-coral disabled:opacity-50"
            />
            <span className="text-[14px] text-ink">{SIGNUP_TERMS_LABEL}</span>
          </label>
          <label className="flex items-start gap-2.5">
            <input
              type="checkbox"
              checked={agreedPrivacy}
              disabled={isSaving}
              onChange={(e) => {
                setAgreedPrivacy(e.target.checked);
                setErrorMessage(null);
              }}
              className="mt-0.5 h-5 w-5 shrink-0 accent-coral disabled:opacity-50"
            />
            <span className="text-[14px] text-ink">{SIGNUP_PRIVACY_LABEL}</span>
          </label>
        </section>

        {errorMessage && (
          <p role="alert" className="rounded-2xl bg-coral/10 px-4 py-3 text-sm text-coral">
            {errorMessage}
          </p>
        )}

        {preferenceNotice && (
          <p role="status" className="rounded-2xl bg-sand px-4 py-3 text-sm text-ink/60">
            {preferenceNotice}
          </p>
        )}

        {isProfileSaved && preferenceNotice ? (
          <PrimaryButton onClick={() => navigate('/', { replace: true })}>
            취향 없이 시작하기
          </PrimaryButton>
        ) : (
          <PrimaryButton onClick={handleComplete} disabled={isSaving}>
            {isSaving ? '저장 중...' : '프로필 설정 완료'}
          </PrimaryButton>
        )}
          </>
        )}
      </main>
    </MobileLayout>
  );
}
