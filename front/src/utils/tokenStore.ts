const TOKENS_KEY = 'auth_tokens';

export interface TokenData {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

export function saveTokens(data: TokenData): void {
  localStorage.setItem(TOKENS_KEY, JSON.stringify(data));
}

export function loadTokens(): TokenData | null {
  const raw = localStorage.getItem(TOKENS_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as TokenData;
  } catch {
    return null;
  }
}

export function getAccessToken(): string | null {
  const tokens = loadTokens();
  if (!tokens) return null;
  if (Date.now() > tokens.expiresAt) return null;
  return tokens.accessToken;
}

export function getRefreshToken(): string | null {
  return loadTokens()?.refreshToken ?? null;
}

export function clearTokens(): void {
  localStorage.removeItem(TOKENS_KEY);
}

export function isAuthenticated(): boolean {
  return getAccessToken() !== null;
}
