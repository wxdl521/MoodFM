import api from './client';

export interface User { id: number; username: string; email: string; avatarUrl?: string }

interface RegisterData { username: string; email: string; password: string }
interface LoginData { account: string; password: string; rememberMe?: boolean }
export interface AuthResponse { accessToken: string; refreshToken: string; accessTokenExpiresIn: number; user: User }

export const authApi = {
  register: (data: RegisterData): Promise<AuthResponse> => api.post('/auth/register', data),
  login:    (data: LoginData): Promise<AuthResponse>    => api.post('/auth/login', data),
  logout:   (token: string): Promise<void>              => api.post('/auth/logout', null, { headers: { Authorization: `Bearer ${token}` } }),
  me:       (): Promise<User>                            => api.get('/user/me'),
};
