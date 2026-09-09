import { BrowserRouter, Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import ProfileEditPage from './pages/ProfileEditPage';
import ExploreListPage from './pages/ExploreListPage';
import TourSpotDetailPage from './pages/TourSpotDetailPage';
import FestivalDetailPage from './pages/FestivalDetailPage';
import MatchingConditionPage from './pages/MatchingConditionPage';
import SoloCoursePage from './pages/SoloCoursePage';
import CheckInPage from './pages/CheckInPage';
import MyPage from './pages/MyPage';
import AdminDashboardPage from './pages/AdminDashboardPage';
import MatchRoomPage from './pages/MatchRoomPage';
import BlockedMembersPage from './pages/BlockedMembersPage';
import FavoritesPage from './pages/FavoritesPage';
import MatchHistoryPage from './pages/MatchHistoryPage';
import AdminReportsPage from './pages/AdminReportsPage';
import AdminRoute from './components/admin/AdminRoute';
import AdminMembersPage from './pages/AdminMembersPage';
import AdminMeetingPointsPage from './pages/AdminMeetingPointsPage';
import AdminInquiriesPage from './pages/AdminInquiriesPage';
import MyInquiriesPage from './pages/MyInquiriesPage';
import InquiryNewPage from './pages/InquiryNewPage';
import InquiryDetailPage from './pages/InquiryDetailPage';
import SanctionNoticeDialog from './components/common/SanctionNoticeDialog';
import SplashGate from './components/splash/SplashGate';

export default function App() {
  return (
    <BrowserRouter>
      {/*
        앱 진입 첫 화면(로고 스플래시)이다. 세션 확인이 끝날 때까지 Routes를 마운트하지 않아,
        미로그인 회원이 홈 화면을 잠깐 본 뒤 로그인으로 튕기는 깜빡임이 생기지 않는다.
      */}
      <SplashGate>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/profile/edit" element={<ProfileEditPage />} />
          <Route path="/spots" element={<ExploreListPage />} />
          <Route path="/spots/:spotId" element={<TourSpotDetailPage />} />
          <Route path="/festivals/:festivalId" element={<FestivalDetailPage />} />
          <Route path="/matching" element={<MatchingConditionPage />} />
          <Route path="/match-room" element={<MatchRoomPage />} />
          <Route path="/solo-course" element={<SoloCoursePage />} />
          <Route path="/check-in" element={<CheckInPage />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/mypage/blocks" element={<BlockedMembersPage />} />
          <Route path="/mypage/matches" element={<MatchHistoryPage />} />
          <Route path="/mypage/favorites" element={<FavoritesPage />} />
        <Route path="/mypage/inquiries" element={<MyInquiriesPage />} />
        <Route path="/mypage/inquiries/new" element={<InquiryNewPage />} />
        <Route path="/mypage/inquiries/:inquiryId" element={<InquiryDetailPage />} />
          <Route path="/admin" element={<AdminRoute><AdminDashboardPage /></AdminRoute>} />
          <Route path="/admin/reports" element={<AdminRoute><AdminReportsPage /></AdminRoute>} />
          <Route path="/admin/members" element={<AdminRoute><AdminMembersPage /></AdminRoute>} />
          <Route path="/admin/meeting-points" element={<AdminRoute><AdminMeetingPointsPage /></AdminRoute>} />
        <Route path="/admin/inquiries" element={<AdminRoute><AdminInquiriesPage /></AdminRoute>} />
        </Routes>
      </SplashGate>
      {/*
        정지 회원이 활동(체크인·매칭·댓글)을 시도해 403을 받으면 apiClient가 이벤트를 쏘고
        여기서 사유·기간 안내를 띄운다. 화면마다 붙이지 않도록 최상단에 한 번만 둔다.
      */}
      <SanctionNoticeDialog />
    </BrowserRouter>
  );
}
