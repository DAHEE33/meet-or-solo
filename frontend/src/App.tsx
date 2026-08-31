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
import AdminReportsPage from './pages/AdminReportsPage';
import AdminRoute from './components/admin/AdminRoute';
import AdminMembersPage from './pages/AdminMembersPage';
import AdminMeetingPointsPage from './pages/AdminMeetingPointsPage';

export default function App() {
  return (
    <BrowserRouter>
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
        <Route path="/admin" element={<AdminRoute><AdminDashboardPage /></AdminRoute>} />
        <Route path="/admin/reports" element={<AdminRoute><AdminReportsPage /></AdminRoute>} />
        <Route path="/admin/members" element={<AdminRoute><AdminMembersPage /></AdminRoute>} />
        <Route path="/admin/meeting-points" element={<AdminRoute><AdminMeetingPointsPage /></AdminRoute>} />
      </Routes>
    </BrowserRouter>
  );
}
