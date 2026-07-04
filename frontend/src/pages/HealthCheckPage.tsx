import { useEffect, useState } from 'react';
import { getHealth, type HealthResponse } from '../api/healthApi';

type HealthState =
  | { status: 'loading' }
  | { status: 'success'; data: HealthResponse }
  | { status: 'error'; message: string };

export function HealthCheckPage() {
  const [health, setHealth] = useState<HealthState>({ status: 'loading' });

  useEffect(() => {
    let ignore = false;

    getHealth()
      .then((data) => {
        if (!ignore) {
          setHealth({ status: 'success', data });
        }
      })
      .catch((error: unknown) => {
        if (!ignore) {
          setHealth({
            status: 'error',
            message: error instanceof Error ? error.message : '알 수 없는 오류'
          });
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  return (
    <main className="health-page">
      <section className="health-panel" aria-labelledby="health-title">
        <p className="eyebrow">개발 연결 확인</p>
        <h1 id="health-title">meet-or-solo</h1>
        <p className="description">
          현재 화면은 frontend에서 backend <code>GET /api/health</code> 연결만 확인하는 개발용 화면입니다.
        </p>

        <div className={`status-box status-${health.status}`} role="status" aria-live="polite">
          {health.status === 'loading' && <p>요청 중입니다.</p>}
          {health.status === 'success' && (
            <>
              <span className="status-label">연결 성공</span>
              <dl>
                <div>
                  <dt>status</dt>
                  <dd>{health.data.status}</dd>
                </div>
                <div>
                  <dt>service</dt>
                  <dd>{health.data.service}</dd>
                </div>
              </dl>
            </>
          )}
          {health.status === 'error' && (
            <>
              <span className="status-label">연결 실패</span>
              <p>{health.message}</p>
              <p className="hint">
                backend local profile 실행 여부와 Vite proxy 설정을 확인합니다.
              </p>
            </>
          )}
        </div>
      </section>
    </main>
  );
}
