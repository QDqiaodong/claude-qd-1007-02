import { createRouter, createWebHashHistory } from 'vue-router'

// 注意：main.js 里没有 app.use(router)，路由实例由 App.vue 直接驱动，
// 所以这里全部用静态 import —— <component :is> 拿到的是组件对象本身。
import RideView from '../views/RideView.vue'
import ShowView from '../views/ShowView.vue'
import FnbView from '../views/FnbView.vue'
import MemberView from '../views/MemberView.vue'

// 首页（卡片网格导航）用 '/' 承载：App.vue 在 path 不在 viewMap 时渲染卡片网格。
const routes = [
  { path: '/', name: 'home', component: { template: '<div></div>' } },
  { path: '/ride', name: 'ride', component: RideView },
  { path: '/show', name: 'show', component: ShowView },
  { path: '/fnb', name: 'fnb', component: FnbView },
  { path: '/member', name: 'member', component: MemberView },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
