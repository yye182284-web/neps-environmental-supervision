import { createStore } from 'vuex'

const STORAGE_KEY = 'neps_user'

function loadUser() {
  const current = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
  if (current) return current
  const legacy = JSON.parse(localStorage.getItem('neps_supervisor') || 'null')
  return legacy ? { ...legacy, role: 'PUBLIC' } : null
}

export default createStore({
  state: {
    user: loadUser()
  },
  getters: {
    isLogin: state => !!state.user,
    user: state => state.user
  },
  mutations: {
    setUser(state, user) {
      state.user = user
      if (user) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
      } else {
        localStorage.removeItem(STORAGE_KEY)
      }
    },
    logout(state) {
      state.user = null
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem('neps_supervisor')
      sessionStorage.removeItem('neps-decision-dashboard')
    }
  },
  actions: {
  },
  modules: {
  }
})
