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
  liked?: boolean;
  favorited?: boolean;
}

export interface CommentItem {
  commentId: number;
  noteId: number;
  userId: number;
  content: string;
  createdAt: number;
  likeCount?: number;
  /** 根评论ID，0 表示自身为根 */
  rootId?: number;
  /** 直接父评论ID */
  parentId?: number;
  /** 楼层号 */
  floor?: number;
  /** 子回复总数 */
  replyCount?: number;
  /** 状态 0:正常 1:隐藏 2:删除 */
  state?: number;
  /** 前N条子回复预览 */
  previewReplies?: CommentItem[];
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
  liked?: boolean;
  favorited?: boolean;
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
  fileId: string;
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
