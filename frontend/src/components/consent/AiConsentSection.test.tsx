import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import AiConsentSection, {
  EMPTY_AI_CONSENT_DRAFT,
  isAiConsentComplete,
} from './AiConsentSection';

function render(props: Partial<Parameters<typeof AiConsentSection>[0]> = {}): string {
  return renderToStaticMarkup(
    <AiConsentSection
      value={EMPTY_AI_CONSENT_DRAFT}
      onChange={() => {}}
      {...props}
    />,
  );
}

describe('isAiConsentComplete', () => {
  it('두 동의를 모두 체크해야 완료로 본다', () => {
    expect(isAiConsentComplete({ aiProcessing: true, overseasTransfer: true })).toBe(true);
  });

  it('AI 처리와 국외 이전은 하나로 합치지 않는다', () => {
    expect(isAiConsentComplete({ aiProcessing: true, overseasTransfer: false })).toBe(false);
    expect(isAiConsentComplete({ aiProcessing: false, overseasTransfer: true })).toBe(false);
    expect(isAiConsentComplete(EMPTY_AI_CONSENT_DRAFT)).toBe(false);
  });
});

describe('AiConsentSection', () => {
  it('동의 항목을 두 개의 별도 체크박스로 그린다', () => {
    const html = render();
    expect(html).toContain('id="consent-ai-processing"');
    expect(html).toContain('id="consent-overseas-transfer"');
    expect((html.match(/type="checkbox"/g) ?? []).length).toBe(2);
  });

  it('해외로 전송된다는 사실을 요약에서 바로 알린다', () => {
    const html = render();
    expect(html).toContain('해외');
    expect(html).toContain('미국');
  });

  it('체크 상태를 그대로 반영한다', () => {
    expect((render().match(/checked=""/g) ?? []).length).toBe(0);
    const partial = render({ value: { aiProcessing: true, overseasTransfer: false } });
    expect((partial.match(/checked=""/g) ?? []).length).toBe(1);
    const both = render({ value: { aiProcessing: true, overseasTransfer: true } });
    expect((both.match(/checked=""/g) ?? []).length).toBe(2);
  });

  it('이미 동의한 경우 체크박스를 잠근다', () => {
    const html = render({
      value: { aiProcessing: true, overseasTransfer: true },
      agreed: true,
    });
    expect((html.match(/disabled/g) ?? []).length).toBeGreaterThanOrEqual(2);
  });

  it('저장 중에는 체크박스를 잠근다', () => {
    const html = render({ disabled: true });
    expect((html.match(/disabled/g) ?? []).length).toBeGreaterThanOrEqual(2);
  });

  it('상세 고지는 기본으로 접혀 있고 펼치는 버튼을 제공한다', () => {
    const html = render();
    expect(html).toContain('자세히');
    expect(html).toContain('aria-expanded="false"');
    // 접힌 상태에서는 상세 항목이 렌더링되지 않는다.
    expect(html).not.toContain('보내는 시점과 방법');
  });

  it('철회 방법과 보내지 않는 정보를 항상 노출한다', () => {
    const html = render();
    expect(html).toContain('닉네임');
    expect(html).toContain('철회');
  });
});
