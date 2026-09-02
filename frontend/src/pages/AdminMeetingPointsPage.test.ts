import { isValidElement, type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { AdminMeetingPoint, AdminMeetingPointUpsertRequest } from '../api/adminMeetingPoints';
import { AdminMeetingPointFormDialogContent, isLastActivePoint, toFormState } from './AdminMeetingPointsPage';

function nodes(node: ReactNode): Array<{ type: unknown; props: Record<string, unknown> }> {
  if (Array.isArray(node)) return node.flatMap(nodes);
  if (!isValidElement(node)) return [];
  return [node as never, ...nodes(node.props.children)];
}
function text(node: ReactNode): string {
  if (Array.isArray(node)) return node.map(text).join('');
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (!isValidElement(node)) return '';
  return text(node.props.children);
}

const point = (id: number, status: AdminMeetingPoint['status']): AdminMeetingPoint => ({
  id, festivalId: 144, kakaoPlaceId: `kakao-${id}`, name: `장소 ${id}`, address: '강원 테스트로 1',
  longitude: 128.1, latitude: 37.1, status, assignmentOrder: id * 10,
  createdAt: '2026-08-26T10:00:00+09:00', updatedAt: '2026-08-26T10:00:00+09:00',
});
const form: AdminMeetingPointUpsertRequest = {
  kakaoPlaceId: 'kakao-1', name: '장소', address: '강원 테스트로 1', longitude: 128.1, latitude: 37.1, assignmentOrder: 10,
};

describe('isLastActivePoint', () => {
  it('ACTIVE 장소가 하나뿐이고 그 장소일 때만 true다', () => {
    expect(isLastActivePoint([point(1, 'ACTIVE'), point(2, 'INACTIVE')], point(1, 'ACTIVE'))).toBe(true);
  });
  it('ACTIVE 장소가 여러 건이면 false다', () => {
    expect(isLastActivePoint([point(1, 'ACTIVE'), point(2, 'ACTIVE')], point(1, 'ACTIVE'))).toBe(false);
  });
  it('대상 장소가 이미 INACTIVE면 false다', () => {
    expect(isLastActivePoint([point(1, 'INACTIVE')], point(1, 'INACTIVE'))).toBe(false);
  });
});

describe('toFormState', () => {
  it('신규 등록은 축제 좌표를 좌표 선택기 초기 중심점으로 채운다', () => {
    const form = toFormState(null, { longitude: 128.1, latitude: 37.1 });

    expect(form.longitude).toBe(128.1);
    expect(form.latitude).toBe(37.1);
    expect(form.kakaoPlaceId).toBe('');
  });

  it('축제 좌표가 없으면 0/0으로 둔다(좌표 선택기가 자체 기본값으로 대체)', () => {
    const form = toFormState(null, { longitude: null, latitude: null });

    expect(form.longitude).toBe(0);
    expect(form.latitude).toBe(0);
  });

  it('수정 모드는 축제 좌표와 무관하게 기존 장소 값을 그대로 쓴다', () => {
    const existing = point(1, 'ACTIVE');

    const form = toFormState(existing, { longitude: 128.9, latitude: 37.9 });

    expect(form.longitude).toBe(existing.longitude);
    expect(form.latitude).toBe(existing.latitude);
    expect(form.kakaoPlaceId).toBe(existing.kakaoPlaceId);
  });
});

describe('AdminMeetingPointFormDialogContent', () => {
  it('신규 등록 모드에서는 활성화 안내 문구를 보여준다', () => {
    const tree = AdminMeetingPointFormDialogContent({
      festivalTitle: '강릉 단오제', isEdit: false, form, submitting: false, error: null,
      onChange: vi.fn(), onClose: vi.fn(), onSubmit: vi.fn(),
    });
    expect(text(tree)).toContain('만남 장소 등록');
    expect(text(tree)).toContain('비활성 상태로 저장됩니다');
  });

  it('수정 모드에서는 활성화 안내 문구를 보여주지 않는다', () => {
    const tree = AdminMeetingPointFormDialogContent({
      festivalTitle: '강릉 단오제', isEdit: true, form, submitting: false, error: null,
      onChange: vi.fn(), onClose: vi.fn(), onSubmit: vi.fn(),
    });
    expect(text(tree)).toContain('만남 장소 수정');
    expect(text(tree)).not.toContain('비활성 상태로 저장됩니다');
  });

  it('제출 중에는 취소·저장 버튼을 비활성화한다', () => {
    const tree = AdminMeetingPointFormDialogContent({
      festivalTitle: '강릉 단오제', isEdit: false, form, submitting: true, error: null,
      onChange: vi.fn(), onClose: vi.fn(), onSubmit: vi.fn(),
    });
    const buttons = nodes(tree).filter((node) => node.type === 'button');
    expect(buttons.every((button) => button.props.disabled === true)).toBe(true);
  });

  it('저장 실패 시 오류 문구를 alert로 보여준다', () => {
    const tree = AdminMeetingPointFormDialogContent({
      festivalTitle: '강릉 단오제', isEdit: false, form, submitting: false, error: new Error('fail'),
      onChange: vi.fn(), onClose: vi.fn(), onSubmit: vi.fn(),
    });
    const alert = nodes(tree).find((node) => node.props.role === 'alert');
    expect(alert?.props['aria-live']).toBe('assertive');
    expect(text(tree)).toContain('저장하지 못했습니다');
  });
});
