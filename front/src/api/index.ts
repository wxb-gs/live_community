import { API_BASE } from '../config';
import { getAccessToken, clearTokens } from '../utils/tokenStore';
import type {
  NoteSummary,
  NoteDetail,
  CommentItem,
  CreateDraftResponse,
  PresignedUrlResponse,
  ApiResult,
  NoteSearchResponse,
  UserSearchResponse,
  SuggestResponse,
} from '../types';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { ...headers, ...((options?.headers as Record<string, string>) ?? {}) },
  });
  if (res.status === 401) {
    clearTokens();
    window.location.href = '/login';
    throw new Error('登录已过期');
  }
  if (res.status === 429) {
    throw new Error('请求太频繁，请稍后再试');
  }
  if (!res.ok) {
    throw new Error(`请求失败 (${res.status})`);
  }
  const json: ApiResult<T> = await res.json();
  if (json.code !== 200) {
    throw new Error(json.msg || '服务器错误');
  }
  return json.data;
}

export async function fetchNotes(page: number, size: number): Promise<NoteSummary[]> {
  return request<NoteSummary[]>(`/api/note/list?page=${page}&size=${size}`);
}

export async function fetchNoteDetail(noteId: number): Promise<NoteDetail> {
  return request<NoteDetail>(`/api/note/detail?noteId=${noteId}`);
}

export async function createDraft(title: string, content: string): Promise<CreateDraftResponse> {
  return request<CreateDraftResponse>('/api/note/draft', {
    method: 'POST',
    body: JSON.stringify({ title, content }),
  });
}

export async function getPresignedUrl(
  fileName: string,
  contentType: string
): Promise<PresignedUrlResponse> {
  return request<PresignedUrlResponse>(
    `/api/upload/presigned?fileName=${encodeURIComponent(fileName)}&contentType=${encodeURIComponent(contentType)}`
  );
}

export function toExternalUrl(url: string): string {
  return url.replace('http://minio:9000', '/minio');
}

export async function uploadToMinio(url: string, file: File): Promise<void> {
  const externalUrl = toExternalUrl(url);
  const res = await fetch(externalUrl, {
    method: 'PUT',
    body: file,
    headers: { 'Content-Type': file.type },
  });
  if (!res.ok) {
    throw new Error(`上传失败 (${res.status})`);
  }
}

export async function publishNote(
  noteId: number,
  fileName: string,
  contentType: string
): Promise<NoteDetail> {
  return request<NoteDetail>('/api/note/publish', {
    method: 'POST',
    body: JSON.stringify({ noteId, fileName, contentType }),
  });
}

export async function addComment(noteId: number, content: string): Promise<CommentItem> {
  return request<CommentItem>('/api/note/comment', {
    method: 'POST',
    body: JSON.stringify({ noteId, content }),
  });
}

export interface UserInfo {
  userId: number;
  username: string | null;
  nickname: string;
  avatar: string;
}

export async function fetchMyProfile(): Promise<UserInfo> {
  return request<UserInfo>('/api/auth/me');
}

export async function getWechatAuthUrl(redirectUri: string): Promise<string> {
  const res = await fetch(`${API_BASE}/api/auth/wechat/url?redirectUri=${encodeURIComponent(redirectUri)}`);
  const json: ApiResult<string> = await res.json();
  if (json.code !== 200) throw new Error(json.msg || '获取微信授权链接失败');
  return json.data;
}

export async function getTaobaoAuthUrl(redirectUri: string): Promise<string> {
  const res = await fetch(`${API_BASE}/api/auth/taobao/url?redirectUri=${encodeURIComponent(redirectUri)}`);
  const json: ApiResult<string> = await res.json();
  if (json.code !== 200) throw new Error(json.msg || '获取淘宝授权链接失败');
  return json.data;
}

export async function sendSmsCode(phone: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/auth/phone/send-code`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone }),
  });
  const json: ApiResult<null> = await res.json();
  if (json.code !== 200) throw new Error(json.msg || '发送验证码失败');
}

export interface LoginData {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserInfo;
}

export async function passwordLogin(username: string, password: string): Promise<LoginData> {
  return request<LoginData>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ type: 'password', username, password }),
  });
}

export async function passwordRegister(username: string, password: string, nickname?: string): Promise<LoginData> {
  const res = await fetch(`${API_BASE}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, nickname: nickname || username }),
  });
  if (res.status === 409) throw new Error('用户名已存在');
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error((err as { msg?: string }).msg || '注册失败');
  }
  const json: ApiResult<LoginData> = await res.json();
  if (json.code !== 200) throw new Error(json.msg || '注册失败');
  return json.data;
}

export async function wechatLogin(code: string): Promise<LoginData> {
  const res = await fetch(`${API_BASE}/api/auth/wechat/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  });
  if (res.status === 401) throw new Error('微信授权已过期，请重新登录');
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error((err as { msg?: string }).msg || '微信登录失败');
  }
  const json: ApiResult<LoginData> = await res.json();
  if (json.code !== 200) throw new Error(json.msg || '微信登录失败');
  return json.data;
}

export async function taobaoLogin(code: string): Promise<LoginData> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'taobao', code }),
  });
  if (res.status === 401) throw new Error('淘宝授权已过期，请重新登录');
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error((err as { msg?: string }).msg || '淘宝登录失败');
  }
  const json: ApiResult<LoginData> = await res.json();
  if (json.code !== 200) throw new Error(json.msg || '淘宝登录失败');
  return json.data;
}

export async function phoneLogin(phone: string, smsCode: string): Promise<LoginData> {
  return request<LoginData>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ type: 'phone', phone, smsCode }),
  });
}

export async function toggleInteraction(
  interactionType: string,
  targetType: string,
  targetId: number
): Promise<{ active: boolean; count: number; action: string }> {
  return request<{ active: boolean; count: number; action: string }>('/api/interaction/toggle', {
    method: 'POST',
    body: JSON.stringify({ interactionType, targetType, targetId }),
  });
}

export async function getInteractionStatus(
  interactionType: string,
  targetType: string,
  targetId: number
): Promise<{ active: boolean; count: number; action: string }> {
  return request<{ active: boolean; count: number; action: string }>(
    `/api/interaction/status?interactionType=${interactionType}&targetType=${targetType}&targetId=${targetId}`
  );
}

export async function batchInteractionStatus(
  interactionType: string,
  targetType: string,
  targetIds: number[]
): Promise<{ statuses: Record<number, { active: boolean; count: number }> }> {
  return request<{ statuses: Record<number, { active: boolean; count: number }> }>(
    '/api/interaction/batch-status',
    {
      method: 'POST',
      body: JSON.stringify({ interactionType, targetType, targetIds }),
    }
  );
}

export async function searchNotes(params: {
  q: string;
  page?: number;
  size?: number;
  category?: string;
  sort?: string;
}): Promise<NoteSearchResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set('q', params.q);
  if (params.page) searchParams.set('page', String(params.page));
  if (params.size) searchParams.set('size', String(params.size));
  if (params.category) searchParams.set('category', params.category);
  if (params.sort) searchParams.set('sort', params.sort);
  return request<NoteSearchResponse>(`/api/search/note?${searchParams.toString()}`);
}

export async function searchUsers(params: {
  q: string;
  page?: number;
  size?: number;
}): Promise<UserSearchResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set('q', params.q);
  if (params.page) searchParams.set('page', String(params.page));
  if (params.size) searchParams.set('size', String(params.size));
  return request<UserSearchResponse>(`/api/search/user?${searchParams.toString()}`);
}

export async function searchSuggest(q: string): Promise<SuggestResponse> {
  return request<SuggestResponse>(`/api/search/suggest?q=${encodeURIComponent(q)}`);
}
