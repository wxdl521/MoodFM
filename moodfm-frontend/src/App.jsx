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
import Profile from './screens/Profile';
import Settings from './screens/Settings';
import Platforms from './screens/Platforms';
import Blacklist from './screens/Blacklist';
import History from './screens/History';
import Loved from './screens/Loved';
import PlaylistList from './screens/PlaylistList';
import SongDetail from './screens/SongDetail';
import ErrorPage from './screens/ErrorPage';

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
        <Route path="/profile"       element={<RequireAuth><Profile /></RequireAuth>} />
        <Route path="/settings"      element={<RequireAuth><Settings /></RequireAuth>} />
        <Route path="/settings/platforms" element={<RequireAuth><Platforms /></RequireAuth>} />
        <Route path="/settings/blacklist" element={<RequireAuth><Blacklist /></RequireAuth>} />
        <Route path="/history"       element={<RequireAuth><History /></RequireAuth>} />
        <Route path="/likes"         element={<RequireAuth><Loved /></RequireAuth>} />
        <Route path="/playlists"     element={<RequireAuth><PlaylistList /></RequireAuth>} />
        <Route path="/song/:id"      element={<RequireAuth><SongDetail /></RequireAuth>} />
        <Route path="*" element={<ErrorPage />} />
      </Routes>
    </BrowserRouter>
  );
}
