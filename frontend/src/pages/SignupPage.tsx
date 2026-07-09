import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { TravelStyle } from '../types';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import Chip from '../components/common/Chip';

const TRAVEL_STYLES: TravelStyle[] = ['느긋하게', '액티브', '맛집탐방', '사진위주', '문화답사'];

export default function SignupPage() {
  const navigate = useNavigate();
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [styles, setStyles] = useState<TravelStyle[]>([]);

  const toggleStyle = (style: TravelStyle) =>
    setStyles((prev) =>
      prev.includes(style) ? prev.filter((s) => s !== style) : [...prev, style],
    );

  const handleSignup = () => {
    // TODO: Spring Boot 회원가입 API 연동
    navigate('/');
  };

  const inputClass =
    'rounded-2xl border border-line bg-white px-4 py-3.5 text-[15px] text-ink outline-none placeholder:text-ink/35 focus:border-coral';

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader title="회원가입" />
      <main className="flex flex-col gap-6 px-5 pb-10 pt-2">
        <div className="flex flex-col gap-3">
          <input
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="닉네임"
            className={inputClass}
          />
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="이메일"
            className={inputClass}
          />
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호"
            className={inputClass}
          />
        </div>

        <section className="flex flex-col gap-3">
          <h2 className="text-[17px] font-bold text-ink">나의 여행 스타일</h2>
          <p className="-mt-2 text-[13px] text-ink/50">매칭 추천에 사용돼요. 여러 개 선택 가능.</p>
          <div className="flex flex-wrap gap-2">
            {TRAVEL_STYLES.map((style) => (
              <Chip
                key={style}
                label={style}
                selected={styles.includes(style)}
                onClick={() => toggleStyle(style)}
              />
            ))}
          </div>
        </section>

        <PrimaryButton onClick={handleSignup}>가입하기</PrimaryButton>
      </main>
    </MobileLayout>
  );
}
