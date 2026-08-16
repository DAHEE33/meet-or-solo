import { useEffect, useState, type ReactNode } from 'react';
import { ApiClientError } from '../../api/apiClient';
import { adminReportsApi, type AdminSession } from '../../api/adminReports';

export type AdminRouteState =
  | { status: 'LOADING'; session: null }
  | { status: 'ALLOWED'; session: AdminSession }
  | { status: 'FORBIDDEN'; session: null }
  | { status: 'ERROR'; session: null };

export function resolveAdminRouteError(error: unknown): AdminRouteState['status'] {
  return error instanceof ApiClientError && error.status === 403 ? 'FORBIDDEN' : 'ERROR';
}

export default function AdminRoute({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AdminRouteState>({ status: 'LOADING', session: null });

  useEffect(() => {
    const controller = new AbortController();
    adminReportsApi.getSession(controller.signal).then((session) => {
      if (!controller.signal.aborted) setState({ status: 'ALLOWED', session });
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) {
        setState({ status: resolveAdminRouteError(error), session: null } as AdminRouteState);
      }
    });
    return () => controller.abort();
  }, []);

  if (state.status === 'LOADING') {
    return <main role="status" className="min-h-screen bg-sand p-8 text-center text-ink/60">관리자 권한을 확인하고 있습니다.</main>;
  }
  if (state.status === 'FORBIDDEN') {
    return <main role="alert" className="min-h-screen bg-sand p-8 text-center"><h1 className="text-xl font-bold text-ink">관리자만 접근할 수 있습니다</h1><p className="mt-2 text-sm text-ink/60">현재 계정에는 관리자 권한이 없습니다.</p></main>;
  }
  if (state.status === 'ERROR') {
    return <main role="alert" className="min-h-screen bg-sand p-8 text-center"><h1 className="text-xl font-bold text-ink">권한을 확인하지 못했습니다</h1><button type="button" onClick={() => window.location.reload()} className="mt-4 rounded-xl border border-line bg-white px-4 py-2 font-semibold">다시 시도</button></main>;
  }
  return <>{children}</>;
}
