import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import Landing from './screens/Landing';
import Auth from './screens/Auth';
import Onboarding from './screens/Onboarding';
import Bind from './screens/Bind';
import Home from './screens/Home';
import Player from './screens/Player';
import Playlist from './screens/Playlist';
import Insights from './screens/Insights';
import Calendar from './screens/Calendar';
import Weekly from './screens/Weekly';

function RequireAuth({ children }) {
  const token = useAuthStore(s => s.token);
  if (!token) return <Navigate to="/auth" replace />;
  return children;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/auth" element={<Auth />} />
        <Route path="/onboarding" element={<RequireAuth><Onboarding /></RequireAuth>} />
        <Route path="/bind" element={<RequireAuth><Bind /></RequireAuth>} />
        <Route path="/home" element={<RequireAuth><Home /></RequireAuth>} />
        <Route path="/player" element={<RequireAuth><Player /></RequireAuth>} />
        <Route path="/playlist/:id" element={<RequireAuth><Playlist /></RequireAuth>} />
        <Route path="/insights"      element={<RequireAuth><Insights /></RequireAuth>} />
        <Route path="/calendar"      element={<RequireAuth><Calendar /></RequireAuth>} />
        <Route path="/weekly/:id"    element={<RequireAuth><Weekly /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
