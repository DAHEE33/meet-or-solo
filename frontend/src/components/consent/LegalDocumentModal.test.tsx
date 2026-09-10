import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import LegalDocumentModal from './LegalDocumentModal';
import ConsentCheckbox from './ConsentCheckbox';
import { TERMS_NOTICE } from './consentNotice';
import { privacyDocument, termsDocument } from './legalDocuments';

describe('LegalDocumentModal', () => {
  it('제목과 버전, 시행일을 함께 보여준다', () => {
    const document = termsDocument();
    const html = renderToStaticMarkup(
      <LegalDocumentModal document={document} onClose={() => {}} />,
    );
    expect(html).toContain(document.title);
    expect(html).toContain(`버전 ${document.version}`);
    expect(html).toContain(document.effectiveDate);
  });

  it('모든 조항을 한 번에 그린다', () => {
    const document = termsDocument();
    const html = renderToStaticMarkup(
      <LegalDocumentModal document={document} onClose={() => {}} />,
    );
    document.sections.forEach((section) => {
      expect(html).toContain(section.heading);
    });
  });

  it('스크린리더가 dialog로 읽을 수 있게 표시한다', () => {
    const html = renderToStaticMarkup(
      <LegalDocumentModal document={privacyDocument()} onClose={() => {}} />,
    );
    expect(html).toContain('role="dialog"');
    expect(html).toContain('aria-modal="true"');
    expect(html).toContain('aria-labelledby="legal-document-title"');
  });

  /** 전문이 길어 본문만 스크롤돼야 한다. 헤더까지 흐르면 닫기 버튼이 화면 밖으로 나간다. */
  it('본문 영역만 스크롤한다', () => {
    const html = renderToStaticMarkup(
      <LegalDocumentModal document={privacyDocument()} onClose={() => {}} />,
    );
    expect(html).toContain('overflow-y-auto');
    expect(html).toContain('max-h-[85vh]');
  });
});

describe('ConsentCheckbox', () => {
  function render(props: Partial<Parameters<typeof ConsentCheckbox>[0]> = {}): string {
    return renderToStaticMarkup(
      <ConsentCheckbox
        id="consent-terms"
        notice={TERMS_NOTICE}
        checked={false}
        disabled={false}
        onChange={() => {}}
        {...props}
      />,
    );
  }

  it('요약은 항상 보이고 상세는 접혀 있다', () => {
    const html = render();
    expect(html).toContain(TERMS_NOTICE.summary);
    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain(TERMS_NOTICE.details[0].value);
  });

  it('전문 보기 버튼은 넘긴 경우에만 그린다', () => {
    expect(render()).not.toContain('약관 전문 보기');
    const html = render({ documentLabel: '약관 전문 보기', onOpenDocument: () => {} });
    expect(html).toContain('약관 전문 보기');
  });

  it('저장 중에는 체크박스를 잠근다', () => {
    expect(render({ disabled: true })).toContain('disabled=""');
    expect(render()).not.toContain('disabled=""');
  });
});
