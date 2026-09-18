import { ShieldAlert } from 'lucide-react';
import type { SanctionNotice } from '../../api/types';
import { formatSeoulDateTime } from '../../utils/dateTime';

type Props = {
  notice: SanctionNotice;
  /**
   * 화면 상단 배너로 쓸 때 강조한다. 마이페이지처럼 다른 카드와 나란히 놓이는 자리에서
   * 흰 카드로 두면 제재 안내가 일반 정보처럼 묻힌다.
   */
  prominent?: boolean;
};

/**
 * 계정 제한 안내 card(docs/19 4.8, 4.4).
 *
 * 세 곳에서 쓴다. 영구정지와 탈퇴 재가입 제한은 로그인 화면(로그인 자체가 막히므로),
 * 이용정지는 활동을 시도해 403을 받은 순간의 dialog(로그인 상태로 조회는 계속하므로)다.
 *
 * 사유 문구는 서버가 만든 reasonMessage를 그대로 쓴다. 화면에서 사유 code를 문구로 바꾸면
 * 신고자 보호 심사를 두 곳에서 해야 한다. 문구 정의는 backend의 `MemberSanctionReason`과
 * `MemberSanctionNotice` 한 곳이다.
 */
export default function AccountRestrictionNotice({ notice, prominent = false }: Props) {
  const withdrawn = notice.status === 'WITHDRAWN';
  const permanent = notice.status === 'BANNED' || (withdrawn && notice.rejoinAvailableAt === null);

  return (
    <section
      role="alert"
      aria-labelledby="account-restriction-title"
      className={prominent
        ? 'rounded-2xl border-2 border-coral bg-coral/5 p-5'
        : 'rounded-2xl bg-white p-5'}
    >
      <div className="flex items-center gap-2">
        <ShieldAlert aria-hidden className="h-5 w-5 text-coral" />
        <h2 id="account-restriction-title" className="text-[15px] font-bold text-ink">
          {withdrawn ? '탈퇴한 계정이에요' : permanent ? '영구정지된 계정이에요' : '이용정지 중이에요'}
        </h2>
      </div>

      <dl className="mt-4 flex flex-col gap-3">
        <div>
          <dt className="text-[13px] text-ink/55">{withdrawn ? '안내' : '제한 사유'}</dt>
          <dd className="mt-0.5 text-[14px] font-semibold text-ink">{notice.reasonMessage}</dd>
        </div>
        <div>
          <dt className="text-[13px] text-ink/55">
            {withdrawn ? '다시 가입할 수 있는 시각' : '제한 기간'}
          </dt>
          <dd className="mt-0.5 text-[14px] font-semibold text-ink">
            {withdrawn
              ? notice.rejoinAvailableAt === null
                ? '다시 가입할 수 없음'
                : formatSeoulDateTime(notice.rejoinAvailableAt)
              : permanent
                ? '기간 제한 없음'
                : `${formatSeoulDateTime(notice.suspendedUntil)}까지`}
          </dd>
        </div>
      </dl>

      <p className="mt-4 text-[13px] leading-5 text-ink/55">
        {withdrawn
          ? notice.rejoinAvailableAt === null
            ? '이 계정으로는 다시 가입할 수 없어요.'
            : '같은 소셜 계정으로 다시 가입하면 프로필을 새로 입력하게 돼요. 탈퇴 전에 이용정지 기간이 남아 있었다면 그 기간은 이어져요.'
          : permanent
            ? '이 계정으로는 다시 로그인할 수 없어요.'
            : '정지 기간에는 체크인, 동행 매칭, 댓글 작성을 할 수 없어요. 축제와 코스 둘러보기는 그대로 이용할 수 있어요.'}
      </p>

      {notice.contactEmail && (
        <p className="mt-4 rounded-xl bg-sand/60 px-4 py-3 text-[13px] leading-5 text-ink/70">
          {withdrawn
            ? '문의가 있으면 고객센터 이메일로 연락주세요.'
            : permanent
              ? '제재에 이의가 있으면 고객센터 이메일로 연락주세요.'
              : '제재 사유가 잘못되었다고 생각되면 고객센터 이메일로 연락주세요.'}
          <br />
          {/* 주소를 문구에 그대로 노출한다. mailto만 걸면 메일 앱이 없는 환경에서 주소를 알 수 없다. */}
          <a href={`mailto:${notice.contactEmail}`} className="font-bold text-coral underline">
            {notice.contactEmail}
          </a>
        </p>
      )}
    </section>
  );
}
