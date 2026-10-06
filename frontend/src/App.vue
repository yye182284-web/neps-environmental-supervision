<template>
  <nav v-if="isLogin" class="top-nav">
    <div class="nav-inner">
      <div class="nav-left">
        <span class="brand"><span class="brand-mark"><i></i></span><span class="brand-copy"><b>NEPS</b><small>{{ roleLabel }}</small></span></span>
        <div class="nav-links">
          <template v-if="user.role === 'PUBLIC' || !user.role">
            <router-link to="/aqiFeedback">我的空气质量反馈</router-link>
            <router-link to="/aqi">AQI 标准</router-link>
            <router-link to="/ai">智能咨询</router-link>
          </template>
          <template v-else-if="user.role === 'GRID_MEMBER'">
            <router-link to="/grid/tasks">检测任务</router-link>
            <router-link to="/aqi">AQI 标准</router-link>
            <router-link to="/ai">智能咨询</router-link>
          </template>
          <template v-else-if="user.role === 'ADMIN'">
            <router-link to="/admin">管理工作台</router-link>
            <router-link to="/aqi">AQI 级别管理</router-link>
            <router-link to="/decision">统计看板</router-link>
            <router-link to="/ai">智能咨询</router-link>
          </template>
          <template v-else>
            <router-link to="/decision">决策统计看板</router-link>
            <router-link to="/aqi">AQI 标准</router-link>
            <router-link to="/ai">智能咨询</router-link>
          </template>
        </div>
      </div>
      <div class="nav-right">
        <span class="system-state"><i></i>系统在线</span>
        <div class="user-pill"><span class="user-avatar">{{ userInitial }}</span><span class="user-info"><b>{{ user.realName }}</b><small>{{ accountText }}</small></span></div>
        <button class="logout-btn" title="退出登录" @click="handleLogout"><span>↗</span> 退出</button>
      </div>
    </div>
  </nav>
  <router-view/>
</template>

<script>
import { mapGetters } from 'vuex'
import { logout as logoutRequest } from './api/role'

export default {
  mounted() {
    window.addEventListener('neps-session-expired', this.handleSessionExpired)
  },
  beforeUnmount() {
    window.removeEventListener('neps-session-expired', this.handleSessionExpired)
  },
  computed: {
    ...mapGetters(['isLogin', 'user']),
    userInitial() {
      return this.user && this.user.realName ? this.user.realName.slice(0, 1) : 'N'
    },
    roleLabel() {
      return ({ PUBLIC: '公众监督员端', GRID_MEMBER: '检测网格员端', ADMIN: '系统管理端', DECISION: '决策分析端' })[this.user?.role || 'PUBLIC']
    },
    accountText() {
      return this.user?.telId || this.user?.gmCode || this.user?.username || ''
    }
  },
  methods: {
    async handleLogout() {
      try { await logoutRequest() } catch (e) { /* 会话可能已失效，仍清除本地状态 */ }
      this.$store.commit('logout')
      this.$router.push('/')
    },
    handleSessionExpired() {
      this.$store.commit('logout')
      if (this.$route.path !== '/') this.$router.push('/')
    }
  }
}
</script>

<style>
:root {
  --neps-primary: #078467;
  --neps-primary-dark: #055f4c;
  --neps-secondary: #176f9f;
  --neps-bg: #f4f7fb;
  --neps-bg-tint: #edf7f3;
  --neps-text: #18364e;
  --neps-muted: #718296;
  --neps-border: #e0e7ed;
  --neps-danger: #cf5656;
  --neps-danger-soft: #fff0f0;
}

#app {
  font-family: "Microsoft YaHei", Inter, Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #213547;
  min-height: 100vh;
}

html, body {
  margin: 0;
  min-width: 320px;
  background: #f3f6fb;
}

.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(248, 252, 251, .86);
  border-bottom: 1px solid rgba(29, 75, 91, .09);
  box-shadow: 0 8px 30px rgba(21, 55, 70, .07);
  backdrop-filter: blur(18px) saturate(140%);
}

.nav-inner {
  max-width: 1440px;
  min-height: 72px;
  margin: 0 auto;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.nav-left, .nav-right, .nav-links {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-right: 22px;
  color: #17395f;
  font-weight: 800;
  white-space: nowrap;
}

.brand-mark, .user-avatar {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: linear-gradient(145deg, #087e63, #156d94);
  color: #fff;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(9, 105, 84, .22);
}
.brand-mark{position:relative;overflow:hidden}.brand-mark:before,.brand-mark:after,.brand-mark i{content:'';position:absolute;border-radius:100% 0 100% 0;background:rgba(255,255,255,.95);transform:rotate(-35deg)}.brand-mark:before{width:8px;height:15px;left:9px;top:8px}.brand-mark:after{width:7px;height:12px;right:8px;bottom:7px}.brand-mark i{width:2px;height:22px;left:16px;top:7px;background:rgba(255,255,255,.65);transform:rotate(26deg)}
.brand-copy{display:flex;flex-direction:column;line-height:1.1}.brand-copy b{font-size:16px;letter-spacing:.9px}.brand-copy small{margin-top:5px;color:#738695;font-size:10px;font-weight:600;letter-spacing:.2px}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 13px;
}
.system-state{display:inline-flex;align-items:center;gap:6px;margin-right:5px;color:#668078;font-size:11px;white-space:nowrap}.system-state i{width:7px;height:7px;border-radius:50%;background:#24a77d;box-shadow:0 0 0 4px rgba(36,167,125,.11)}
.user-pill{display:flex;align-items:center;gap:9px;padding:6px 10px 6px 7px;border:1px solid rgba(37,76,94,.1);border-radius:13px;background:rgba(255,255,255,.72)}

.top-nav a {
  padding: 10px 13px;
  border: 0;
  border-radius: 9px;
  color: #526579;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: color .2s, border-color .2s;
}

.top-nav a:hover,
.top-nav a.router-link-exact-active {
  color: #197a63;
  background: linear-gradient(135deg,#e6f6f0,#ecf5f8);
}

.user-info {
  color: #607286;
  font-size: 12px;
  white-space: nowrap;
}
.user-info b,.user-info small{display:block}.user-info b{color:#29475b;font-size:12px}.user-info small{margin-top:2px;color:#8a9aa6;font-size:10px}

.logout-btn {
  padding: 8px 12px;
  border: 1px solid #dce4ec;
  border-radius: 10px;
  background: rgba(255,255,255,.75);
  color: #d64b4b;
  cursor: pointer;
}

.logout-btn:hover {
  border-color: #e88787;
  background: #fff5f5;
}
.logout-btn span{display:inline-block;transform:rotate(135deg)}

@media (max-width: 760px) {
  .nav-inner { position: relative; padding: 10px 74px 10px 14px; align-items: flex-start; }
  .nav-left { width: 100%; flex-wrap: wrap; gap: 7px; }
  .brand { width: 100%; margin: 0 0 3px; }
  .nav-links{flex-wrap:wrap}.top-nav a { padding: 7px 9px; }
  .system-state,.user-pill { display: none; }
  .nav-right { position: absolute; right: 12px; top: 9px; }
  .logout-btn { margin-top: 0; }
}
</style>
