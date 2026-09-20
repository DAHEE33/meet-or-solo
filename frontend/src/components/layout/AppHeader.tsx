import { Bell } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Wordmark from '../brand/Wordmark';
import {
  markAllReadEverywhere,
  useNotifications,
  type StoredNotification,
} from '../../notifications/notificationStore';
import { formatSeoulDateTime } from '../../utils/dateTime';

export default function AppHeader() {
  const navigate = useNavigate();
  const { items, unreadCount, retention } = useNotifications();
  const [open, setOpen] = useState(false);

  const toggle = () => {
    setOpen((previous) => {
      // 여는 순간 읽음으로 본다. 목록을 펼쳤는데 뱃지가 남아 있으면 무엇이 새 알림인지
      // 구분할 방법이 없다. 읽음은 서버에도 기록해 다른 기기에서 다시 뱃지가 뜨지 않게 한다.
      if (!previous) void markAllReadEverywhere();
      return !previous;
    });
  };

  const openNotification = (notification: StoredNotification) => {
    setOpen(false);
    navigate(notification.message.path);
  };

  return (
    <header className="sticky top-0 z-20 bg-sand/90 px-5 pb-2 pt-4 backdrop-blur">
      <div className="flex items-center justify-between">
        <Wordmark className="text-xl font-extrabold" />
        <button
          type="button"
          aria-label={unreadCount > 0 ? `알림 ${unreadCount}건` : '알림'}
          aria-expanded={open}
          onClick={toggle}
          className="relative flex h-11 w-11 items-center justify-center rounded-full text-ink active:bg-black/5"
        >
          <Bell size={22} strokeWidth={1.8} />
          {unreadCount > 0 && (
            <span className="absolute right-1.5 top-1.5 min-w-[18px] rounded-full bg-coral px-1 text-center text-[11px] font-bold leading-[18px] text-white">
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </button>
      </div>

      {open && (
        <div
          role="dialog"
          aria-label="알림 목록"
          className="absolute right-5 top-16 z-30 w-[min(20rem,calc(100vw-2.5rem))] overflow-hidden rounded-2xl border border-line bg-white shadow-[0_8px_24px_rgba(34,48,62,0.12)]"
        >
          {items.length === 0 ? (
            <p className="px-4 py-6 text-center text-[13px] text-ink/50">아직 받은 알림이 없어요.</p>
          ) : (
            <ul className="max-h-[60vh] divide-y divide-line overflow-y-auto">
              {items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => openNotification(item)}
                    className="block w-full px-4 py-3 text-left active:bg-black/5"
                  >
                    <p className="text-[14px] font-semibold text-ink">{item.message.title}</p>
                    {item.message.body && (
                      <p className="mt-0.5 text-[12px] text-ink/60">{item.message.body}</p>
                    )}
                    <p className="mt-1 text-[11px] text-ink/40">
                      {formatSeoulDateTime(item.occurredAt)}
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {/*
            보관 범위를 화면에 박아두지 않고 서버 값으로 적는다. 1단계에서 "이 기기에
            보관해요"를 박아둔 탓에 서버 알림함이 생긴 뒤에도 문구가 남아 있었다(docs/32 3.3).
          */}
          <p className="border-t border-line px-4 py-2 text-[11px] text-ink/40">
            {retention
              ? `최근 ${retention.count}건, ${retention.days}일까지 보관해요.`
              : '최근 알림만 보여줘요.'}
          </p>
        </div>
      )}
    </header>
  );
}
