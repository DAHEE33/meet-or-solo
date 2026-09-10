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
import ConsentCheckbox from '../components/consent/ConsentCheckbox';
import LegalDocumentModal from '../components/consent/LegalDocumentModal';
import { PRIVACY_NOTICE, TERMS_NOTICE } from '../components/consent/consentNotice';
import { legalDocument, type LegalDocumentId } from '../components/consent/legalDocuments';
import PreferenceInputSection from '../components/preference/PreferenceInputSection';
import { preferenceSignupNotice } from '../components/preference/preferenceStatus';
import {
  EMPTY_PREFERENCE_DRAFT,
  PREFERENCE_TEXT_MAX_LENGTH,
  buildPreferenceText,
  isPreferenceDraftComplete,
  type PreferenceDraft,
} from '../components/preference/preferenceText';
import { NICKNAME_MAX_LENGTH, NICKNAME_RULE_MESSAGE, validateNickname } from '../utils/nickname';
import { LoadingState } from '../components/common/Spinner';

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
  /** 전문을 띄운 문서. 모달이라 열어도 입력한 프로필과 취향이 남는다. */
  const [openDocument, setOpenDocument] = useState<LegalDocumentId | null>(null);
  /**
   * 전문을 한 번이라도 연 문서. 열기 전에는 동의 체크를 잠근다.
   *
   * 판정 시점을 "닫을 때"가 아니라 "열 때"로 잡았다. 닫는 경로가 `확인했어요`와 `X` 두 개라
   * 어느 하나가 빠지면 영구히 잠긴 체크박스가 되고, 그건 가입 자체를 막는 버그가 된다.
   */
  const [viewedDocuments, setViewedDocuments] = useState<LegalDocumentId[]>([]);
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

  const openLegalDocument = (id: LegalDocumentId) => {
    setOpenDocument(id);
    setViewedDocuments((prev) => (prev.includes(id) ? prev : [...prev, id]));
  };

  const viewedTerms = viewedDocuments.includes('TERMS');
  const viewedPrivacy = viewedDocuments.includes('PRIVACY');

  /** 잠긴 이유와 풀렸다는 사실을 같은 자리에서 알린다. 체크를 마치면 안내를 거둔다. */
  const consentHint = (viewed: boolean, checked: boolean, documentName: string): string | undefined => {
    if (!viewed) return `${documentName} 전문을 확인하면 동의할 수 있어요.`;
    if (!checked) return `${documentName}을 확인했어요. 동의에 체크해 주세요.`;
    return undefined;
  };

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
      const saved = await preferenceEmbeddingApi.createOrUpdate(preferenceText);
      // 저장은 200이어도 분석은 실패할 수 있다. 가입은 막지 않고 다시 시도할 곳만 알려준다.
      return preferenceSignupNotice(saved.embeddingStatus);
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
    // 체크가 잠긴 상태에서 완료를 누르면 "동의해 주세요"는 막다른 안내가 된다.
    if (!viewedTerms || !viewedPrivacy) {
      setErrorMessage('이용약관과 개인정보처리방침 전문을 먼저 확인해 주세요.');
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
          <LoadingState className="py-10" message="프로필을 불러오는 중이에요" />
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

        {/*
          필수 동의는 전문을 확인한 뒤에만 체크할 수 있다. 요약은 `자세히`로 펼치고 전문은
          `전문 보기` 모달로 띄우며, 전문을 열기 전에는 체크박스가 잠긴다. 잠금이 풀려도
          체크는 사용자가 직접 누른다 — `확인했어요`를 동의로 간주하지 않는다.
          동의 저장 흐름(agreeAll)은 바꾸지 않았다.
        */}
        <section className="flex flex-col gap-4 border-t border-line pt-5">
          <ConsentCheckbox
            id="consent-terms"
            notice={TERMS_NOTICE}
            checked={agreedTerms}
            disabled={isSaving || !viewedTerms}
            onChange={(checked) => {
              setAgreedTerms(checked);
              setErrorMessage(null);
            }}
            documentLabel="약관 전문 보기"
            onOpenDocument={() => openLegalDocument('TERMS')}
            hint={consentHint(viewedTerms, agreedTerms, '이용약관')}
          />
          <ConsentCheckbox
            id="consent-privacy"
            notice={PRIVACY_NOTICE}
            checked={agreedPrivacy}
            disabled={isSaving || !viewedPrivacy}
            onChange={(checked) => {
              setAgreedPrivacy(checked);
              setErrorMessage(null);
            }}
            documentLabel="처리방침 전문 보기"
            onOpenDocument={() => openLegalDocument('PRIVACY')}
            hint={consentHint(viewedPrivacy, agreedPrivacy, '개인정보처리방침')}
          />
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

      {openDocument && (
        <LegalDocumentModal
          document={legalDocument(openDocument)}
          onClose={() => setOpenDocument(null)}
        />
      )}
    </MobileLayout>
  );
}
