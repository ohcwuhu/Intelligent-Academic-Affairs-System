/** 与后端 com.iaas 各 DTO 一一对应的类型。字段名保持与接口一致，不做本地改名。 */

export type Role = 'ADMIN' | 'ACADEMIC' | 'TEACHER' | 'STUDENT'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: Role
  refId: number | null
}

export interface LoginResponse {
  token: string
  expiresIn: number
  user: UserInfo
}

/** 一条选课记录（含成绩）。 */
export interface MyCourse {
  enrollmentId: number
  teachingClassId: number
  teachingClassCode: string
  courseCode: string
  courseName: string
  credit: number
  courseType: string
  teacherName: string | null
  termName: string | null
  classroom: string | null
  timeText: string
  weekday: number
  startSection: number
  endSection: number
  score: number | null
  scoreStatus: string
  gradePoint: number | null
  enrollStatus: string
}

export interface CreditSummary {
  earnedCredit: number
  inProgressCredit: number
  gpa: number
  passedCourses: number
  failedCourses: number
  inProgressCourses: number
}

export interface ConflictItem {
  courseA: string
  timeA: string
  courseB: string
  timeB: string
}

export interface SelectResult {
  enrollmentId: number
  message: string
  conflicts: ConflictItem[]
}

export interface TeachingClassVO {
  id: number
  code: string
  courseId: number
  courseCode: string
  courseName: string
  credit: number
  courseType: string
  teacherId: number
  teacherName: string
  termId: number
  termName: string
  capacity: number
  enrolled: number
  remaining: number
  weekday: number
  startSection: number
  endSection: number
  startWeek: number
  endWeek: number
  weekType: string
  classroom: string
  status: string
  timeText: string
}

export interface RosterItem {
  enrollmentId: number
  studentId: number
  studentNo: string
  studentName: string
  clazzName: string | null
  majorName: string | null
  score: number | null
  scoreStatus: string
  gradePoint: number | null
  passed: boolean
}

export interface StudentVO {
  id: number
  studentNo: string
  name: string
  gender: string
  birthDate: string | null
  phone: string | null
  email: string | null
  collegeId: number
  collegeName: string
  majorId: number
  majorName: string
  clazzId: number
  clazzName: string
  grade: number
  status: string
}

export interface Course {
  id: number
  code: string
  name: string
  credit: number
  hours: number
  courseType: string
  collegeId: number
  assessType: string
  status: number
}

export interface Teacher {
  id: number
  teacherNo: string
  name: string
  gender: string
  title: string | null
  collegeId: number
  phone: string | null
  email: string | null
  status: string
}

export interface College {
  id: number
  code: string
  name: string
}

export interface Major {
  id: number
  code: string
  name: string
  collegeId: number
}

export interface Clazz {
  id: number
  code: string
  name: string
  majorId: number
  grade: number
}

export interface Term {
  id: number
  code: string
  name: string
  startDate: string
  endDate: string
  isCurrent: number
}

export interface TimetableEntry {
  courseName: string
  courseCode: string
  teacherName: string | null
  className: string | null
  classroom: string
  weekday: number
  startSection: number
  endSection: number
  timeText: string
  credit: number
}

export interface Timetable {
  termId: number
  entries: TimetableEntry[]
}

export interface MajorTimetable {
  termId: number
  termName: string | null
  majorId: number | null
  majorName: string | null
  grade: number | null
  courseCount: number
  entries: TimetableEntry[]
}

export interface ScheduleConflict {
  type: 'TEACHER' | 'CLASSROOM'
  conflictWith: string
  timeText: string
  teachingClassId: number
}

export interface TeachingClassSaveResult {
  id: number
  conflicts: ScheduleConflict[]
}

export interface AppUser {
  id: number
  username: string
  realName: string
  role: Role
  refId: number | null
  status: number
  lastLogin: string | null
}

// ---- 智能问答与治理 ----

export interface AssistantStatus {
  enabled: boolean
  llmReady: boolean
  provider: string
  model: string
  message: string
}

/** 一条引用。excerpt 是逐字原文，不改写。 */
export interface Citation {
  chunkId: number
  documentId: number
  documentTitle: string
  docNo: string | null
  dept: string
  effectiveDate: string | null
  hierarchyPath: string
  articleNo: string | null
  excerpt: string
}

/** 一次问答的结果。intent 与 mode 是能力边界，界面据实呈现，不美化。 */
export interface AssistantAnswer {
  intent: 'RULE' | 'PERSONAL' | 'PROCESS' | 'OUT_OF_SCOPE' | 'AMBIGUOUS'
  mode: 'generated' | 'extractive' | 'process' | 'tool' | 'refusal' | 'clarify'
  answer: string
  citations: Citation[]
  notes: string[]
  data: Record<string, unknown> | null
  conversationId: number | null
  durationMs: number
}

export interface NoticeRow {
  id: number
  title: string
  content: string
  publisher: string | null
  targetRole: string | null
  pinned: boolean
  publishedAt: string | null
}

export interface MessageRow {
  id: number
  studentNo: string | null
  studentName: string | null
  content: string
  reply: string | null
  repliedBy: string | null
  repliedAt: string | null
  createdAt: string | null
}

export interface TextbookRow {
  textbookId: number
  teachingClassId: number
  teachingClassCode: string | null
  courseName: string | null
  courseCode: string | null
  title: string
  author: string | null
  publisher: string | null
  isbn: string | null
  price: number | null
  note: string | null
  ordered: boolean
}

export interface MyTextbooks {
  rows: TextbookRow[]
  orderedCount: number
  orderedAmount: number
  totalAmount: number
}

export interface GradeComponent {
  id: number | null
  enrollmentId: number
  item: string
  weight: number | null
  score: number | null
}

export interface StudentComponents {
  enrollmentId: number
  studentNo: string | null
  studentName: string | null
  totalScore: number | null
  items: GradeComponent[]
}

export interface CourseComponents {
  courseCode: string | null
  courseName: string | null
  termName: string | null
  totalScore: number | null
  scoreStatus: string | null
  items: GradeComponent[]
}

export interface FeeRule {
  id: number
  item: string
  creditPrice: number
  note: string | null
  effectiveFrom: string | null
  status: number
}

export interface FeeBillItem {
  courseCode: string | null
  courseName: string
  credit: number | null
  item: string
  unitPrice: number
  amount: number
  reason: string
}

export interface FeeBill {
  studentId: number
  studentNo: string
  studentName: string
  termId: number | null
  termName: string | null
  items: FeeBillItem[]
  total: number
  notes: string[]
}

export interface Certificate {
  no: string
  certName: string
  kind: string
  studentName: string | null
  studentNo: string | null
  gender: string | null
  collegeName: string | null
  majorName: string | null
  clazzName: string | null
  grade: number | null
  issuedDate: string
  termName: string | null
  creditSummary: string | null
  lines: string[]
  notes: string[]
}

export interface ClassroomOccupancy {
  classroom: string
  weekday: number
  weekdayText: string
  startSection: number
  endSection: number
  sectionText: string
  courseName: string | null
  courseCode: string | null
  teachingClassCode: string
  teacherName: string | null
  startWeek: number | null
  endWeek: number | null
  weekType: string
}

export interface ClassroomSlot {
  termId: number | null
  termName: string | null
  weekday: number
  weekdayText: string
  startSection: number
  endSection: number
  busy: ClassroomOccupancy[]
  freeRooms: string[]
}

export interface ProgramRow {
  id: number
  title: string
  majorId: number | null
  majorName: string
  grade: number | null
  degree: string | null
  duration: string | null
  minCredit: number | null
  sourceNote: string | null
  status: string
  importedAt: string | null
  moduleCount: number
  courseCount: number
  courseCreditSum: number
}

export interface ProgramModuleRow {
  category: string
  hoursText: string | null
  credit: number
  ratio: number | null
}

export interface ProgramCourseRow {
  id: number | null
  module: string
  groupName: string | null
  courseName: string
  courseType: string
  assessType: string | null
  credit: number
  totalHours: number | null
  labHours: number | null
  computerHours: number | null
  termNo: number | null
  weekHours: number | null
  note: string | null
  required: string | null
  courseId: number | null
  courseCode: string | null
}

export interface ProgramDetail {
  program: ProgramRow
  modules: ProgramModuleRow[]
  courses: ProgramCourseRow[]
}

export interface ModuleAudit {
  category: string
  required: number
  earned: number
  gap: number
  planCourses: number
  passedCourses: number
  missing: string[]
}

export interface ProgramAudit {
  studentId: number
  studentNo: string
  studentName: string
  programId: number | null
  programTitle: string | null
  majorName: string | null
  grade: number | null
  minCredit: number | null
  earned: number | null
  gap: number | null
  complete: boolean
  modules: ModuleAudit[]
  passedOutsidePlan: ProgramCourseRow[]
  notes: string[]
}

export interface ImportTarget {
  type: string
  label: string
  note: string
  columns: string[]
  sample: string[]
}

export interface ImportRowResult {
  line: number
  key: string | null
  ok: boolean
  message: string | null
}

export interface ImportReport {
  type: string
  label: string
  fileName: string
  total: number
  ok: number
  failed: number
  committed: boolean
  rows: ImportRowResult[]
  errors: string[]
}

export interface ExamRow {
  id: number
  teachingClassId: number
  teachingClassCode: string | null
  courseCode: string | null
  courseName: string | null
  teacherName: string | null
  termId: number | null
  termName: string | null
  examType: string
  examDate: string
  startTime: string
  endTime: string
  timeText: string
  classroom: string | null
  seatNo: string | null
  note: string | null
  daysAhead: number | null
  conflictWith: string[]
}

export interface ExamSaveResult {
  id: number
  conflicts: { kind: string; message: string }[]
}

export interface ApplicationRow {
  id: number
  type: string
  typeText: string
  status: string
  studentNo: string | null
  studentName: string | null
  termId: number | null
  termName: string | null
  target: string
  reason: string
  materials: string | null
  precheckNote: string | null
  reviewer: string | null
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string | null
}

export interface ApplicationOption {
  id: number
  label: string
  note: string | null
}

export interface ApplicationSubmitResult {
  id: number
  status: string
  message: string
  precheckNote: string | null
}

export interface ChatConversation {
  id: number
  userId: number
  title: string
  turnCount: number
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  conversationId: number
  role: 'user' | 'assistant' | string
  content: string
  intent: string | null
  mode: string | null
  citationPaths: string | null
  createdAt: string
}

export interface KnowledgeChunkDetail {
  id: number
  documentId: number
  hierarchyPath: string
  articleNo: string | null
  content: string
}

export interface KnowledgeStats {
  documents: number
  chunks: number
}

export interface KnowledgeDocumentRow {
  id: number
  title: string
  docNo: string | null
  dept: string
  effectiveDate: string | null
  expireDate: string | null
  scope: string
  visibility: string
  status: string
  auditor: string | null
  chunkCount: number
}

export interface GovernanceOverview {
  pendingFeedback: number
  pendingGap: number
  askToday: number
  blockedToday: number
  injectionToday: number
  avgDurationMs: number
}

export interface FeedbackRow {
  id: number
  userId: number
  username: string | null
  role: string | null
  question: string
  answerMode: string | null
  answerDigest: string | null
  citationPath: string | null
  type: 'USEFUL' | 'USELESS' | 'WRONG'
  detail: string | null
  status: string
  handler: string | null
  handleNote: string | null
  handledAt: string | null
  createdAt: string
}

export interface KnowledgeGapRow {
  id: number
  questionKey: string
  sampleQuestion: string
  hitCount: number
  reason: string | null
  dept: string | null
  status: string
  assignee: string | null
  note: string | null
  handledAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AuditRow {
  id: number
  eventType: string
  username: string | null
  role: string | null
  question: string | null
  intent: string | null
  mode: string | null
  hitCount: number | null
  citationCount: number | null
  blocked: number
  reason: string | null
  durationMs: number | null
  createdAt: string
}
