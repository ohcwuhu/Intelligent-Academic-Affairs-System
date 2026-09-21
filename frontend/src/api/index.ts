import { del, get, post, postForm } from './client'
import type {
  AppUser,
  ApplicationOption,
  ApplicationRow,
  ApplicationSubmitResult,
  AssistantAnswer,
  AssistantStatus,
  AuditRow,
  ChatConversation,
  ChatMessage,
  Clazz,
  College,
  Course,
  CreditSummary,
  ConflictItem,
  ExamRow,
  ExamSaveResult,
  FeedbackRow,
  ImportReport,
  ImportTarget,
  GovernanceOverview,
  KnowledgeChunkDetail,
  KnowledgeDocumentRow,
  KnowledgeGapRow,
  KnowledgeStats,
  LoginResponse,
  Major,
  MajorTimetable,
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
  major: (params: { majorId?: number; grade?: number; termId?: number }) =>
    get<MajorTimetable>('/schedule/major', params),
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

export const importApi = {
  targets: () => get<ImportTarget[]>('/import/targets'),
  dictionary: () =>
    get<{ colleges: Record<string, string>; majors: Record<string, string>; clazzes: Record<string, string> }>(
      '/import/dictionary',
    ),
  preview: (type: string, file: File) => {
    const form = new FormData()
    form.append('type', type)
    form.append('file', file)
    return postForm<ImportReport>('/import/preview', form)
  },
  commit: (type: string, file: File) => {
    const form = new FormData()
    form.append('type', type)
    form.append('file', file)
    return postForm<ImportReport>('/import/commit', form)
  },
}

export const examApi = {
  /** 学生：我的考试（只看本人选课的教学班） */
  my: (termId?: number) => get<ExamRow[]>('/exam/my', { termId }),
  /** 教务看全部，教师只看本人任教教学班 */
  list: (params: { termId?: number; teachingClassId?: number }) => get<ExamRow[]>('/exam', params),
  save: (body: Record<string, unknown>) => post<ExamSaveResult>('/exam', body),
  remove: (id: number) => del<void>(`/exam/${id}`),
}

export const applicationApi = {
  /** 类型清单返回 "编码|名称"，前端拆开当选项用 */
  types: () => get<string[]>('/application/types'),
  options: (type: string) => get<ApplicationOption[]>('/application/options', { type }),
  submit: (body: {
    type: string
    targetId?: number | null
    target?: string
    reason: string
    materials?: string
  }) => post<ApplicationSubmitResult>('/application', body),
  mine: () => get<ApplicationRow[]>('/application/mine'),
  withdraw: (id: number) => post<void>(`/application/${id}/withdraw`),
  page: (params: Record<string, unknown>) => get<PageResult<ApplicationRow>>('/application', params),
  review: (id: number, action: 'APPROVE' | 'REJECT', note?: string) =>
    post<ApplicationRow>(`/application/${id}/review`, { action, note }),
}

export const assistantApi = {
  status: () => get<AssistantStatus>('/assistant/status'),
  ask: (question: string, conversationId?: number | null) =>
    post<AssistantAnswer>('/assistant/ask', { question, conversationId }),
  conversations: () => get<ChatConversation[]>('/assistant/conversations'),
  conversationMessages: (id: number) => get<ChatMessage[]>(`/assistant/conversations/${id}`),
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
  expiring: (days = 30) => get<KnowledgeDocumentRow[]>('/knowledge/documents/expiring', { days }),
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
