<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import router from './router'
import api from './api'

import RideView from './views/RideView.vue'
import ShowView from './views/ShowView.vue'
import FnbView from './views/FnbView.vue'
import MemberView from './views/MemberView.vue'

// ------------------------------------------------------------------
// 导航形态：首页以「卡片网格」展示四个业务模块，每张大卡片点击进入对应视图。
// 路由实例由 main.js 之外的地方（这里）直接驱动：
//   - 不调用 useRoute()，改为读取 router.currentRoute.value.path
//   - 用 <component :is> 渲染当前模块视图
// ------------------------------------------------------------------

const viewMap = {
  '/ride': RideView,
  '/show': ShowView,
  '/fnb': FnbView,
  '/member': MemberView
}

const modules = [
  { path: '/ride', icon: '🎢', title: '游乐设施', desc: '过山车 · 旋转木马 · 身高/年龄限制与开放校验', key: 'ride' },
  { path: '/show', icon: '🎭', title: '园区演出', desc: '巡游 · 剧场 · 容量预订与超售拦截', key: 'show' },
  { path: '/fnb', icon: '🍔', title: '餐饮网点', desc: '快餐 · 正餐 · 饮品 多业态经营', key: 'fnb' },
  { path: '/member', icon: '🎟️', title: '会员与年卡', desc: '会员等级 · 共享主键年卡/季卡', key: 'member' }
]

const currentPath = ref(router.currentRoute.value.path)
const counts = ref({ ride: 0, show: 0, fnb: 0, member: 0 })
const currentView = computed(() => viewMap[currentPath.value] || null)

function go(path) {
  router.push(path)
}

async function loadCounts() {
  try {
    const [r, s, f, m] = await Promise.all([
      api.listRides(), api.listShows(), api.listFnb(), api.listMembers()
    ])
    counts.value = {
      ride: (r || []).length,
      show: (s || []).length,
      fnb: (f || []).length,
      member: (m || []).length
    }
  } catch (e) {
    // 计数失败不影响主流程
  }
}

watch(() => router.currentRoute.value.path, (p) => { currentPath.value = p })
onMounted(async () => {
  currentPath.value = router.currentRoute.value.path
  await loadCounts()
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">游</span>
        <div class="brand-text">
          <div class="brand-title">欢乐谷游乐园 · 园区运营管理</div>
          <div class="brand-sub">设施 · 演出 · 餐饮 · 会员年卡</div>
        </div>
      </div>
      <el-button v-if="currentView" @click="go('/')">← 返回模块首页</el-button>
    </header>

    <main class="main">
      <!-- 首页：模块卡片网格 -->
      <div v-if="!currentView">
        <div class="home-title">业务模块（点击进入）</div>
        <div class="card-grid">
          <div v-for="m in modules" :key="m.path" class="mod-card" @click="go(m.path)">
            <div class="mod-icon">{{ m.icon }}</div>
            <div class="mod-title">{{ m.title }}</div>
            <div class="mod-desc">{{ m.desc }}</div>
            <div class="mod-count">{{ counts[m.key] }} 条记录</div>
          </div>
        </div>
      </div>

      <!-- 模块视图 -->
      <component v-else :is="currentView" />
    </main>
  </div>
</template>
