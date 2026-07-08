import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { TravelStyle } from '../types';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import Chip from '../components/common/Chip';

const TRAVEL_STYLES: TravelStyle[] = ['느긋하게', '액티브', '맛집탐방', '사진위주', '문화답사'];
const THEMES = ['한옥', '야시장', '사진', '카페', '역사', '야경', '시장', '산책'];
const TIMES = ['지금 바로', '1시간 이내', '오늘 오후', '오늘 저녁'];

export default function MatchingConditionPage() {
  const navigate = useNavigate();
  const [styles, setStyles] = useState<TravelStyle[]>(['느긋하게']);
  const [themes, setThemes] = useState<string[]>(['한옥']);
  const [time, setTime] = useState<string>('지금 바로');

  const toggle = <T,>(list: T[], v: T): T[] =>
    list.includes(v) ? list.filter((x) => x !== v) : [...list, v];

  return (
    <MobileLayout>
      <PageHeader title="매칭 조건 설정" noBack />
      <main className="flex flex-col gap-7 px-5 pb-10 pt-2">
        <section className="flex flex-col gap-3">
          <h2 className="text-[17px] font-bold text-ink">여행 스타일</h2>
          <div className="flex flex-wrap gap-2">
            {TRAVEL_STYLES.map((s) => (
              <Chip
                key={s}
                label={s}
                selected={styles.includes(s)}
                onClick={() => setStyles((prev) => toggle(prev, s))}
              />
            ))}
          </div>
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="text-[17px] font-bold text-ink">관심 테마</h2>
          <div className="flex flex-wrap gap-2">
            {THEMES.map((t) => (
              <Chip
                key={t}
                label={t}
                selected={themes.includes(t)}
                onClick={() => setThemes((prev) => toggle(prev, t))}
              />
            ))}
          </div>
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="text-[17px] font-bold text-ink">만남 시간</h2>
          <div className="flex flex-wrap gap-2">
            {TIMES.map((t) => (
              <Chip key={t} label={t} selected={time === t} onClick={() => setTime(t)} />
            ))}
          </div>
        </section>

        <PrimaryButton
          disabled={styles.length === 0}
          onClick={() => navigate('/matching/results')}
        >
          이 조건으로 매칭 시작
        </PrimaryButton>
      </main>
    </MobileLayout>
  );
}
