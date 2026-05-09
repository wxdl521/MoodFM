import api from './client';

interface User { id: number; username: string; email: string; avatarUrl?: string }

interface RegisterData { username: string; email: string; password: string }
interface LoginData { email: string; password: string }
interface AuthResponse { token: string; user: User }

export const authApi = {
  register: (data: RegisterData): Promise<AuthResponse> => api.post('/auth/register', data),
  login:    (data: LoginData): Promise<AuthResponse>    => api.post('/auth/login', data),
  me:       (): Promise<User>                            => api.get('/user/me'),
};
