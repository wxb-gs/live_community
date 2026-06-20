import { API_BASE, MOCK_USER } from '../config';
import type {
  NoteSummary,
  NoteDetail,
  CommentItem,
  CreateDraftResponse,
  PresignedUrlResponse,
  ApiResult,
} from '../types';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
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
    body: JSON.stringify({
      userId: MOCK_USER.userId,
      title,
      content,
    }),
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
    body: JSON.stringify({
      noteId,
      userId: MOCK_USER.userId,
      content,
    }),
  });
}
