import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import MobileLayout from '../components/layout/MobileLayout';
import PrimaryButton from '../components/common/PrimaryButton';

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleLogin = () => {
    // TODO: Spring Boot 로그인 API 연동
    navigate('/');
  };

  return (
    <MobileLayout showTabBar={false}>
      <main className="flex min-h-screen flex-col justify-center gap-8 px-6 pb-16">
        <div className="flex flex-col gap-2">
          <div className="flex items-baseline gap-1 text-2xl font-extrabold tracking-tight">
            <span className="text-ink">meet</span>
            <span className="text-coral">·or·</span>
            <span className="text-ink">solo</span>
          </div>
          <p className="text-[15px] text-ink/60">혼자 온 여행, 함께가 될 수도 있으니까</p>
        </div>

        <div className="flex flex-col gap-3">
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="이메일"
            className="rounded-2xl border border-line bg-white px-4 py-3.5 text-[15px] text-ink outline-none placeholder:text-ink/35 focus:border-coral"
          />
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호"
            className="rounded-2xl border border-line bg-white px-4 py-3.5 text-[15px] text-ink outline-none placeholder:text-ink/35 focus:border-coral"
          />
          <PrimaryButton onClick={handleLogin} className="mt-2">
            로그인
          </PrimaryButton>
        </div>

        <p className="text-center text-[13px] text-ink/50">
          아직 계정이 없나요?{' '}
          <Link to="/signup" className="font-semibold text-coral">
            회원가입
          </Link>
        </p>
      </main>
    </MobileLayout>
  );
}
