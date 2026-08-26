import {
  PREFERENCE_TEXT_MAX_LENGTH,
  preferenceTextLength,
  type PreferenceDraft,
} from './preferenceText';

/** 가이드 문항 정의. 문구를 바꿀 때 회원가입·프로필 수정 화면이 함께 바뀐다. */
const GUIDE_QUESTIONS = [
  {
    key: 'activity' as const,
    label: '축제에서 뭘 제일 하고 싶으세요?',
    placeholder: '예: 공연 보다가 사진 찍으면서 천천히 돌아보고 싶어요',
  },
  {
    key: 'companion' as const,
    label: '어떤 사람과 함께면 편할까요?',
    placeholder: '예: 말 많지 않고 각자 페이스대로 다니는 사람이 좋아요',
  },
];

interface PreferenceInputSectionProps {
  value: PreferenceDraft;
  onChange: (draft: PreferenceDraft) => void;
  /** 섹션 제목. null이면 제목을 그리지 않는다(회원가입 단계 등에서 상위 제목을 쓸 때). */
  title?: string | null;
  description?: string;
  disabled?: boolean;
}

/**
 * 취향 입력 공통 컴포넌트. 가이드 2문항과 자유 입력을 함께 받는다.
 *
 * 상태를 직접 들고 있지 않은 controlled 컴포넌트이므로 프로필 수정, 회원가입 흐름,
 * 매칭 신청 전 입력 유도 화면에서 그대로 재사용한다.
 */
export default function PreferenceInputSection({
  value,
  onChange,
  title = '취향 전격 분석',
  description = '두 문항만 답하면 나와 잘 맞는 사람을 찾아드려요.',
  disabled = false,
}: PreferenceInputSectionProps) {
  const totalLength = preferenceTextLength(value);
  const isOverLimit = totalLength > PREFERENCE_TEXT_MAX_LENGTH;

  const fieldClass =
    'resize-none rounded-2xl border border-line bg-white px-4 py-3.5 text-[14px] text-ink outline-none placeholder:text-ink/30 focus:border-coral disabled:bg-sand disabled:text-ink/40';

  return (
    <div className="flex flex-col gap-4">
      {title !== null && (
        <div className="flex flex-col gap-1">
          <h2 className="text-[17px] font-bold text-ink">{title}</h2>
          {description && <p className="text-[13px] text-ink/50">{description}</p>}
        </div>
      )}

      {GUIDE_QUESTIONS.map((question) => (
        <label key={question.key} className="flex flex-col gap-2">
          <span className="text-[14px] font-semibold text-ink">{question.label}</span>
          <textarea
            value={value[question.key]}
            onChange={(event) => onChange({ ...value, [question.key]: event.target.value })}
            disabled={disabled}
            rows={2}
            placeholder={question.placeholder}
            className={fieldClass}
          />
        </label>
      ))}

      <label className="flex flex-col gap-2">
        <span className="text-[14px] font-semibold text-ink">
          더 하고 싶은 말 <span className="font-normal text-ink/40">(선택)</span>
        </span>
        <textarea
          value={value.free}
          onChange={(event) => onChange({ ...value, free: event.target.value })}
          disabled={disabled}
          rows={3}
          placeholder="예: 매운 음식은 잘 못 먹어요. 사람 많은 곳은 조금 피하고 싶어요."
          className={fieldClass}
        />
      </label>

      <p className={`text-right text-[12px] ${isOverLimit ? 'text-coral' : 'text-ink/35'}`}>
        {totalLength} / {PREFERENCE_TEXT_MAX_LENGTH}자
      </p>
    </div>
  );
}
