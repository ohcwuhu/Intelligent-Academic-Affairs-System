<script setup lang="ts">
/** 我的档案。学生只能看到本人档案，这一点由服务端保证。 */
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { authApi, studentApi, userApi } from '@/api'
import type { StudentVO } from '@/api/types'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import StateHost from '@/components/StateHost.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'

const me = ref<StudentVO | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

async function load() {
  state.value = 'loading'
  try {
    me.value = await studentApi.me()
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '档案加载失败'
  }
}

onMounted(load)

/**
 * 自助改口令。
 *
 * 要验原口令：管理员重置是"忘了口令"的场景，本人改是"我记得旧口令"的场景。
 * 不验的话，谁拿到你的登录会话就能把口令改掉，把自己锁在外面。
 */
const pwd = ref({ oldPassword: '', password: '', confirm: '' })
const changing = ref(false)

async function changePassword() {
  if (pwd.value.password.length < 6) {
    toast('新口令至少 6 位', 'bad')
    return
  }
  if (pwd.value.password !== pwd.value.confirm) {
    toast('两次输入的新口令不一致', 'bad')
    return
  }
  changing.value = true
  try {
    const meInfo = await authApi.me()
    await userApi.changeMyPassword(meInfo.id, pwd.value.oldPassword, pwd.value.password)
    toast('口令已修改，下次登录请用新口令', 'ok', 6000)
    pwd.value = { oldPassword: '', password: '', confirm: '' }
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '修改失败', 'bad')
  } finally {
    changing.value = false
  }
}
</script>

<template>
  <Plate title="我的档案" note="学籍信息由教务处维护，如有出入请联系所在院系教学办">
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="没有查到档案"
      empty-detail="请联系教务处确认学籍信息是否已录入。"
      :skeleton-rows="5"
      @retry="load"
    >
      <dl v-if="me" class="sheet">
        <div class="sheet__row"><dt>学号</dt><dd class="num">{{ me.studentNo }}</dd></div>
        <div class="sheet__row"><dt>姓名</dt><dd>{{ me.name }}</dd></div>
        <div class="sheet__row"><dt>性别</dt><dd>{{ me.gender }}</dd></div>
        <div class="sheet__row"><dt>出生日期</dt><dd class="num">{{ me.birthDate ?? '未登记' }}</dd></div>
        <div class="sheet__row"><dt>年级</dt><dd class="num">{{ me.grade }}</dd></div>
        <div class="sheet__row"><dt>学院</dt><dd>{{ me.collegeName }}</dd></div>
        <div class="sheet__row"><dt>专业</dt><dd>{{ me.majorName }}</dd></div>
        <div class="sheet__row"><dt>班级</dt><dd>{{ me.clazzName }}</dd></div>
        <div class="sheet__row"><dt>学籍状态</dt><dd>{{ me.status }}</dd></div>
        <div class="sheet__row"><dt>联系电话</dt><dd class="num">{{ me.phone ?? '未登记' }}</dd></div>
        <div class="sheet__row"><dt>电子邮箱</dt><dd class="num">{{ me.email ?? '未登记' }}</dd></div>
      </dl>
    </StateHost>
  </Plate>

  <Plate title="修改口令" note="改完立即生效，其它设备上的登录不受影响，但下次登录要用新口令">
    <div class="pwd">
      <FieldRow label="原口令" for-id="old-pwd">
        <input id="old-pwd" v-model="pwd.oldPassword" type="password" autocomplete="current-password" />
      </FieldRow>
      <FieldRow label="新口令" for-id="new-pwd" hint="至少 6 位">
        <input id="new-pwd" v-model="pwd.password" type="password" autocomplete="new-password" />
      </FieldRow>
      <FieldRow label="确认新口令" for-id="new-pwd2">
        <input id="new-pwd2" v-model="pwd.confirm" type="password" autocomplete="new-password" />
      </FieldRow>
      <div class="pwd__act">
        <Btn variant="solid" :loading="changing" @click="changePassword">修改口令</Btn>
      </div>
    </div>
  </Plate>
</template>

<style scoped>
.pwd {
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
  padding: var(--s-4);
  max-width: 420px;
}
.pwd__act {
  display: flex;
  justify-content: flex-end;
}
.sheet {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.sheet__row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: var(--s-3);
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}

.sheet__row:nth-child(odd) {
  border-right: 1px solid var(--line);
}

.sheet__row dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
  padding-top: 2px;
}

.sheet__row dd {
  margin: 0;
  font-size: var(--t-sm);
}

@media (max-width: 900px) {
  .sheet {
    grid-template-columns: 1fr;
  }
  .sheet__row:nth-child(odd) {
    border-right: 0;
  }
}
</style>
