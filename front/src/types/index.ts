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
  coverUrl: string;
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

export interface NoteSearchResult {
  id: number;
  userId: number;
  title: string;
  summary: string;
  tags: string;
  category: string;
  viewCount: number;
  likeCount: number;
  createdAt: number;
}

export interface NoteSearchResponse {
  total: number;
  page: number;
  size: number;
  results: NoteSearchResult[];
}

export interface UserSearchResult {
  id: number;
  username: string;
  nickname: string;
  avatar: string;
}

export interface UserSearchResponse {
  total: number;
  page: number;
  size: number;
  results: UserSearchResult[];
}

export interface Suggestion {
  text: string;
  type: 'note' | 'user';
  id: number;
}

export interface SuggestResponse {
  suggestions: Suggestion[];
}
