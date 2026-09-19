import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { TOKEN_KEY } from '@/api/client'
import type { UserInfo } from '@/api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<UserInfo | null>(null)
  const ready = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role ?? null)
  const isStudent = computed(() => role.value === 'STUDENT')
  const isTeacher = computed(() => role.value === 'TEACHER')
  const isStaff = computed(() => role.value === 'ADMIN' || role.value === 'ACADEMIC')
  const isAdmin = computed(() => role.value === 'ADMIN')

  async function login(username: string, password: string) {
    const res = await authApi.login(username, password)
    token.value = res.token
    user.value = res.user
    localStorage.setItem(TOKEN_KEY, res.token)
    ready.value = true
    return res.user
  }

  /** 刷新页面后用令牌换回身份；令牌失效时静默清空。 */
  async function restore() {
    if (!token.value) {
      ready.value = true
      return
    }
    try {
      user.value = await authApi.me()
    } catch {
      token.value = null
      user.value = null
      localStorage.removeItem(TOKEN_KEY)
    } finally {
      ready.value = true
    }
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return {
    token, user, ready, isLoggedIn, role, isStudent, isTeacher, isStaff, isAdmin,
    login, restore, logout,
  }
})
