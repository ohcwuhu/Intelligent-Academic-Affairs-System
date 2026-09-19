import { del, get, post } from './client'
import type {
  AppUser,
  AssistantAnswer,
  AssistantStatus,
  AuditRow,
  Clazz,
  College,
  Course,
  CreditSummary,
  ConflictItem,
  FeedbackRow,
  GovernanceOverview,
  KnowledgeChunkDetail,
  KnowledgeDocumentRow,
  KnowledgeGapRow,
  KnowledgeStats,
  LoginResponse,
  Major,
  MyCourse,
  PageResult,
  RosterItem,
  SelectResult,
  StudentVO,
  Teacher,
  TeachingClassSaveResult,
  TeachingClassVO,
  Term,
  Timetable,
  UserInfo,
} from './types'

export const authApi = {
  login: (username: string, password: string) =>
    post<LoginResponse>('/auth/login', { username, password }),
  me: () => get<UserInfo>('/auth/me'),
}

export const enrollmentApi = {
  my: (termId?: number) => get<MyCourse[]>('/enrollment/my', { termId }),
  summary: () => get<CreditSummary>('/enrollment/summary'),
  conflicts: (termId?: number) => get<ConflictItem[]>('/enrollment/conflicts', { termId }),
  preview: (teachingClassId: number) =>
    get<ConflictItem[]>(`/enrollment/preview/${teachingClassId}`),
  select: (teachingClassId: number) =>
    post<SelectResult>(`/enrollment/select/${teachingClassId}`),
  drop: (enrollmentId: number) => del<void>(`/enrollment/${enrollmentId}`),
}

export const teachingClassApi = {
  list: (params: { termId?: number; courseId?: number; onlyOpen?: boolean }) =>
    get<TeachingClassVO[]>('/teaching-class', params),
  selectable: (params: { termId?: number; keyword?: string }) =>
    get<TeachingClassVO[]>('/teaching-class/selectable', params),
  get: (id: number) => get<TeachingClassVO>(`/teaching-class/${id}`),
  roster: (id: number) => get<RosterItem[]>(`/teaching-class/${id}/roster`),
  save: (body: Record<string, unknown>) =>
    post<TeachingClassSaveResult>('/teaching-class', body),
  remove: (id: number) => del<void>(`/teaching-class/${id}`),
}

export const gradeApi = {
  save: (enrollmentId: number, score: number | null, scoreStatus?: string) =>
    post<void>('/grade/save', { enrollmentId, score, scoreStatus }),
  batch: (entries: { enrollmentId: number; score: number | null; scoreStatus?: string }[]) =>
    post<number>('/grade/batch', entries),
}

export const scheduleApi = {
  my: (termId?: number) => get<Timetable>('/schedule/my', { termId }),
}

export const studentApi = {
  page: (params: Record<string, unknown>) => get<PageResult<StudentVO>>('/student', params),
  me: () => get<StudentVO>('/student/me'),
  save: (body: Record<string, unknown>) => post<number>('/student', body),
  remove: (id: number) => del<void>(`/student/${id}`),
}

export const teacherApi = {
  page: (params: Record<string, unknown>) => get<PageResult<Teacher>>('/teacher', params),
  save: (body: Record<string, unknown>) => post<number>('/teacher', body),
}

export const courseApi = {
  page: (params: Record<string, unknown>) => get<PageResult<Course>>('/course', params),
  save: (body: Record<string, unknown>) => post<number>('/course', body),
}

export const basicApi = {
  colleges: () => get<College[]>('/basic/colleges'),
  majors: (collegeId?: number) => get<Major[]>('/basic/majors', { collegeId }),
  clazzes: (majorId?: number) => get<Clazz[]>('/basic/clazzes', { majorId }),
  terms: () => get<Term[]>('/basic/terms'),
  currentTerm: () => get<Term>('/basic/current-term'),
}

export const userApi = {
  page: (params: Record<string, unknown>) => get<PageResult<AppUser>>('/user', params),
  create: (body: Record<string, unknown>) => post<number>('/user', body),
  resetPassword: (id: number, password: string) =>
    post<void>(`/user/${id}/password`, { password }),
  setStatus: (id: number, status: number) => post<void>(`/user/${id}/status`, {}, { status }),
}

export const assistantApi = {
  status: () => get<AssistantStatus>('/assistant/status'),
  ask: (question: string, conversationId?: number | null) =>
    post<AssistantAnswer>('/assistant/ask', { question, conversationId }),
}

export const knowledgeApi = {
  stats: () => get<KnowledgeStats>('/knowledge/stats'),
  chunk: (id: number) => get<KnowledgeChunkDetail>(`/knowledge/chunks/${id}`),
  documents: () => get<KnowledgeDocumentRow[]>('/knowledge/documents'),
  gate: (id: number) => get<string[]>(`/knowledge/documents/${id}/gate`),
  chunkQuality: (id: number) =>
    get<{ documentId: number; total: number; pass: number; issueCount: number; issues: { chunkId: number; hierarchyPath: string; issue: string }[] }>(
      `/knowledge/documents/${id}/chunk-quality`,
    ),
  submit: (id: number) => post<void>(`/knowledge/documents/${id}/submit`),
  publish: (id: number, force = false, reason?: string) =>
    post<void>(`/knowledge/documents/${id}/publish`, {}, { force, reason }),
  expire: (id: number, reason?: string) =>
    post<void>(`/knowledge/documents/${id}/expire`, {}, { reason }),
  reingest: () => post<number>('/knowledge/reingest'),
}

export const feedbackApi = {
  submit: (body: {
    question: string
    type: 'USEFUL' | 'USELESS' | 'WRONG'
    detail?: string
    answerMode?: string
    answerDigest?: string
    citationPath?: string
  }) => post<number>('/feedback', body),
  mine: () => get<FeedbackRow[]>('/feedback/mine'),
  page: (params: Record<string, unknown>) => get<PageResult<FeedbackRow>>('/feedback', params),
  handle: (id: number, status: string, note?: string) =>
    post<void>(`/feedback/${id}/handle`, { status, note }),
}

export const governanceApi = {
  overview: () => get<GovernanceOverview>('/governance/overview'),
  gaps: (params: Record<string, unknown>) =>
    get<PageResult<KnowledgeGapRow>>('/governance/gaps', params),
  topGaps: (limit = 10) => get<KnowledgeGapRow[]>('/governance/gaps/top', { limit }),
  handleGap: (id: number, status: string, assignee?: string, note?: string) =>
    post<void>(`/governance/gaps/${id}/handle`, { status, assignee, note }),
  audit: (params: Record<string, unknown>) => get<PageResult<AuditRow>>('/governance/audit', params),
}
