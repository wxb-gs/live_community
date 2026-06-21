export interface NoteSummary {
  noteId: number;
  userId: number;
  title: string;
  summary: string;
  coverUrl: string;
  status: string;
  createdAt: number;
  updatedAt: number;
  likeCount?: number;
  favoriteCount?: number;
}

export interface CommentItem {
  commentId: number;
  noteId: number;
  userId: number;
  content: string;
  createdAt: number;
  likeCount?: number;
}

export interface NoteDetail {
  noteId: number;
  userId: number;
  title: string;
  content: string;
  summary: string;
  uploadUrl: string;
  objectKey: string;
  status: string;
  createdAt: number;
  updatedAt: number;
  likeCount?: number;
  favoriteCount?: number;
  comments: CommentItem[];
}

export interface ToggleResult {
  active: boolean;
  count: number;
  action: string;
}

export interface CreateDraftResponse {
  noteId: number;
  status: string;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  objectKey: string;
  expiresAt: number;
}

export interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}
