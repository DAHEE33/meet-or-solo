import ConsentCheckbox from './ConsentCheckbox';
import {
  AI_CONSENT_FOOTNOTE,
  AI_PROCESSING_NOTICE,
  OVERSEAS_TRANSFER_NOTICE,
} from './consentNotice';

export interface AiConsentDraft {
  aiProcessing: boolean;
  overseasTransfer: boolean;
}

export const EMPTY_AI_CONSENT_DRAFT: AiConsentDraft = {
  aiProcessing: false,
  overseasTransfer: false,
};

export function isAiConsentComplete(draft: AiConsentDraft): boolean {
  return draft.aiProcessing && draft.overseasTransfer;
}

interface AiConsentSectionProps {
  value: AiConsentDraft;
  onChange: (draft: AiConsentDraft) => void;
  disabled?: boolean;
  /** 이미 동의를 마친 경우 안내만 보여주고 체크박스를 잠근다. */
  agreed?: boolean;
}

/**
 * 취향 분석에 필요한 두 가지 동의를 받는 공통 컴포넌트.
 *
 * AI 처리와 국외 이전은 법적 성격과 거부 선택이 달라 하나의 체크박스로 합치지 않는다.
 * 상태를 직접 들고 있지 않은 controlled 컴포넌트이므로 회원가입과 프로필 수정에서 그대로
 * 재사용한다.
 */
export default function AiConsentSection({
  value,
  onChange,
  disabled = false,
  agreed = false,
}: AiConsentSectionProps) {
  return (
    <div className="flex flex-col gap-3 rounded-2xl bg-sand/60 p-4">
      <ConsentCheckbox
        id="consent-ai-processing"
        notice={AI_PROCESSING_NOTICE}
        checked={value.aiProcessing}
        disabled={disabled || agreed}
        onChange={(checked) => onChange({ ...value, aiProcessing: checked })}
      />
      <ConsentCheckbox
        id="consent-overseas-transfer"
        notice={OVERSEAS_TRANSFER_NOTICE}
        checked={value.overseasTransfer}
        disabled={disabled || agreed}
        onChange={(checked) => onChange({ ...value, overseasTransfer: checked })}
      />
      <p className="text-[12px] leading-5 text-ink/45">{AI_CONSENT_FOOTNOTE}</p>
    </div>
  );
}
