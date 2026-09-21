import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import type { Role } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

/** 路由表。meta.nav 为真者出现在左侧索引列，meta.roles 决定谁能进。 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/assistant',
    name: 'assistant',
    component: () => import('@/views/AssistantView.vue'),
    meta: {
      title: '智能问答',
      nav: true,
      group: '问答',
      roles: ['STUDENT', 'TEACHER', 'ACADEMIC', 'ADMIN'],
    },
  },
  {
    path: '/me/timetable',
    name: 'my-timetable',
    component: () => import('@/views/TimetableView.vue'),
    meta: { title: '我的课表', nav: true, group: '学业', roles: ['STUDENT'] },
  },
  {
    path: '/me/select',
    name: 'my-select',
    component: () => import('@/views/SelectCourseView.vue'),
    meta: { title: '选课', nav: true, group: '学业', roles: ['STUDENT'] },
  },
  {
    path: '/me/grades',
    name: 'my-grades',
    component: () => import('@/views/GradesView.vue'),
    meta: { title: '成绩与学分', nav: true, group: '学业', roles: ['STUDENT'] },
  },
  {
    path: '/me/profile',
    name: 'my-profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { title: '我的档案', nav: true, group: '学业', roles: ['STUDENT'] },
  },
  {
    path: '/me/applications',
    name: 'my-applications',
    component: () => import('@/views/MyApplicationsView.vue'),
    meta: { title: '我的申请', nav: true, group: '办事', roles: ['STUDENT'] },
  },
  {
    path: '/teach/classes',
    name: 'teach-classes',
    component: () => import('@/views/TeachingClassesMine.vue'),
    meta: { title: '我的教学班', nav: true, group: '教学', roles: ['TEACHER'] },
  },
  {
    path: '/teach/timetable',
    name: 'teach-timetable',
    component: () => import('@/views/TimetableView.vue'),
    meta: { title: '我的课表', nav: true, group: '教学', roles: ['TEACHER'] },
  },
  {
    path: '/teach/classes/:id',
    name: 'teach-roster',
    component: () => import('@/views/RosterView.vue'),
    meta: { title: '教学班名单', roles: ['TEACHER', 'ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/students',
    name: 'admin-students',
    component: () => import('@/views/StudentsView.vue'),
    meta: { title: '学生档案', nav: true, group: '教务', roles: ['ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/teachers',
    name: 'admin-teachers',
    component: () => import('@/views/TeachersView.vue'),
    meta: { title: '教师档案', nav: true, group: '教务', roles: ['ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/courses',
    name: 'admin-courses',
    component: () => import('@/views/CoursesView.vue'),
    meta: { title: '课程库', nav: true, group: '教务', roles: ['ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/classes',
    name: 'admin-classes',
    component: () => import('@/views/TeachingClassesView.vue'),
    meta: { title: '教学班开课', nav: true, group: '教务', roles: ['ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/basic',
    name: 'admin-basic',
    component: () => import('@/views/BasicDataView.vue'),
    meta: { title: '基础数据', nav: true, group: '教务', roles: ['ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/applications',
    name: 'admin-applications',
    component: () => import('@/views/ApplicationReviewView.vue'),
    meta: { title: '申请审批', nav: true, group: '教务', roles: ['ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/knowledge',
    name: 'admin-knowledge',
    component: () => import('@/views/KnowledgeView.vue'),
    meta: { title: '知识库治理', nav: true, group: '治理', roles: ['ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/governance',
    name: 'admin-governance',
    component: () => import('@/views/GovernanceView.vue'),
    meta: { title: '反馈与缺口', nav: true, group: '治理', roles: ['ACADEMIC', 'ADMIN'] },
  },
  {
    path: '/admin/accounts',
    name: 'admin-accounts',
    component: () => import('@/views/AccountsView.vue'),
    meta: { title: '账号管理', nav: true, group: '治理', roles: ['ADMIN'] },
  },
  { path: '/', name: 'home', redirect: '/login' },
  { path: '/:pathMatch(.*)*', name: 'not-found', redirect: '/login' },
]

/** 各角色登录后的着陆页。 */
export function homeForRole(role: Role | null): string {
  if (role === 'STUDENT') return '/me/timetable'
  if (role === 'TEACHER') return '/teach/classes'
  return '/admin/students'
}

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.ready) {
    await auth.restore()
  }
  if (to.meta.public) {
    // 已登录再进登录页，直接送回各自首页
    return auth.isLoggedIn && to.name === 'login' ? homeForRole(auth.role) : true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login' }
  }
  const roles = to.meta.roles as Role[] | undefined
  if (roles && auth.role && !roles.includes(auth.role)) {
    return homeForRole(auth.role)
  }
  return true
})

export default router
