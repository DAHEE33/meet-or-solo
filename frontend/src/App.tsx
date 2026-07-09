import { BrowserRouter, Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import TourSpotListPage from './pages/TourSpotListPage';
import TourSpotDetailPage from './pages/TourSpotDetailPage';
import MatchingConditionPage from './pages/MatchingConditionPage';
import MatchingResultPage from './pages/MatchingResultPage';
import MeetingPointPage from './pages/MeetingPointPage';
import SoloCoursePage from './pages/SoloCoursePage';
import CheckInPage from './pages/CheckInPage';
import MyPage from './pages/MyPage';
import AdminDashboardPage from './pages/AdminDashboardPage';
import { HealthCheckPage } from './pages/HealthCheckPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/spots" element={<TourSpotListPage />} />
        <Route path="/spots/:spotId" element={<TourSpotDetailPage />} />
        <Route path="/matching" element={<MatchingConditionPage />} />
        <Route path="/matching/results" element={<MatchingResultPage />} />
        <Route path="/meeting-point" element={<MeetingPointPage />} />
        <Route path="/solo-course" element={<SoloCoursePage />} />
        <Route path="/check-in" element={<CheckInPage />} />
        <Route path="/mypage" element={<MyPage />} />
        <Route path="/admin" element={<AdminDashboardPage />} />
        <Route path="/health" element={<HealthCheckPage />} />
      </Routes>
    </BrowserRouter>
  );
}
