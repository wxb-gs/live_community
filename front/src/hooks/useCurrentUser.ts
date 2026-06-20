import { getAccessToken } from '../utils/tokenStore';

interface CurrentUser {
  userId: number;
  username: string;
  avatar: string;
}

export function useCurrentUser(): CurrentUser | null {
  const token = getAccessToken();
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return {
      userId: Number(payload.sub),
      username: payload.username || '',
      avatar: payload.avatar || '',
    };
  } catch {
    return null;
  }
}
