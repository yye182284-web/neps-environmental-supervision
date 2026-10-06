import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import store from '../store'
import { getCurrentUser } from '../api/role'

const routes = [
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue')
  },
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/about',
    name: 'about',
    component: () => import(/* webpackChunkName: "about" */ '../views/AboutView.vue')
  },
  {
    path: '/aqiFeedback',
    name: 'aqiFeedback',
    component: () => import('../views/AqiFeedBackList.vue'),
    meta: { requiresAuth: true, roles: ['PUBLIC'] }
  },
  {
    path: '/aqiFeedbackForm',
    name: 'aqiFeedbackForm',
    component: () => import('../views/AqiFeedBackForm.vue'),
    meta: { requiresAuth: true, roles: ['PUBLIC'] }
  },
  {
    path: '/aqi',
    name: 'aqi',
    component: () => import('../views/AqiList.vue'),
    meta: { requiresAuth: true, roles: ['PUBLIC', 'GRID_MEMBER', 'ADMIN', 'DECISION'] }
  },
  {
    path: '/aqiForm',
    name: 'aqiForm',
    component: () => import('../views/AqiForm.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/grid/tasks',
    name: 'gridTasks',
    component: () => import('../views/GridTasks.vue'),
    meta: { requiresAuth: true, roles: ['GRID_MEMBER'] }
  },
  {
    path: '/admin',
    name: 'adminDashboard',
    component: () => import('../views/AdminDashboard.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/decision',
    name: 'decisionDashboard',
    component: () => import('../views/DecisionDashboard.vue'),
    meta: { requiresAuth: true, roles: ['DECISION', 'ADMIN'] }
  },
  {
    path: '/ai',
    name: 'ai',
    component: () => import('../views/AiChat.vue'),
    meta: { requiresAuth: true, roles: ['PUBLIC', 'GRID_MEMBER', 'ADMIN', 'DECISION'] }
  }
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
})

let sessionChecked = false

router.beforeEach(async (to, from, next) => {
  if (store.getters.isLogin && !sessionChecked) {
    try {
      const response = await getCurrentUser()
      store.commit('setUser', response.data.data)
    } catch (e) {
      store.commit('logout')
    } finally {
      sessionChecked = true
    }
  }
  const isLogin = store.getters.isLogin
  const role = store.getters.user?.role || 'PUBLIC'
  if (to.matched.some(record => record.meta.requiresAuth) && !isLogin) {
    next({ path: '/' })
  } else if (to.meta.roles && !to.meta.roles.includes(role)) {
    next({ path: roleHome(role) })
  } else if (isLogin && (to.path === '/' || to.path === '/register')) {
    next({ path: roleHome(role) })
  } else {
    next()
  }
})

function roleHome(role) {
  return ({ PUBLIC: '/aqiFeedback', GRID_MEMBER: '/grid/tasks', ADMIN: '/admin', DECISION: '/decision' })[role] || '/'
}

export default router
