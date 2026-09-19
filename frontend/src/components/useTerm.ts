import { ref } from 'vue'
import { basicApi } from '@/api'
import type { Term } from '@/api/types'

/** 当前学期在多个页面都要用，做成模块级缓存，避免每次进页面都请求一次。 */
const currentTerm = ref<Term | null>(null)
let loading: Promise<Term | null> | null = null

export function useCurrentTerm() {
  async function load(force = false): Promise<Term | null> {
    if (currentTerm.value && !force) return currentTerm.value
    if (!loading || force) {
      loading = basicApi
        .currentTerm()
        .then((t) => {
          currentTerm.value = t
          return t
        })
        .catch(() => null)
        .finally(() => {
          loading = null
        })
    }
    return loading
  }
  return { currentTerm, load }
}
