<template>
  <main class="auth-page">
    <section class="intro-panel">
      <div class="intro-content">
        <div class="intro-brand"><i><b></b></i><span>NEPS ENVIRONMENT INTELLIGENCE</span></div>
        <div class="eyebrow">NEPS · ENVIRONMENT WATCH</div>
        <h1>空气质量公众监督<br>与网格化管理平台</h1>
        <p>贯通公众反馈、属地化任务调度、现场监测与决策分析，构建规范、可信、可追溯的环境信息处理流程。</p>
        <div class="role-summary"><span><i>01</i>公众反馈</span><span><i>02</i>网格检测</span><span><i>03</i>系统管理</span><span><i>04</i>决策分析</span></div>
        <div class="platform-state"><span><i></i>环境监测服务运行中</span><b>NEPS / 2026</b></div>
      </div>
    </section>

    <section class="auth-panel">
      <transition name="toast"><div v-if="toast.show" class="toast">{{ toast.message }}</div></transition>
      <div class="auth-card">
        <div class="card-brand"><i></i><span>NEPS</span><small>统一身份认证</small></div>
        <p class="welcome">欢迎使用 NEPS</p>
        <h2>{{ roleName }}登录</h2>
        <p class="subheading">请选择身份并输入对应账号</p>
        <div class="role-tabs">
          <button v-for="item in roles" :key="item.value" type="button" :class="{ active: role === item.value }" @click="selectRole(item.value)">
            <b>{{ item.icon }}</b><span>{{ item.short }}</span>
          </button>
        </div>
        <form @submit.prevent="handleLogin">
          <label>{{ accountLabel }}</label>
          <div class="input-wrap"><span>◎</span><input v-model.trim="form.account" autocomplete="username" :placeholder="accountPlaceholder"></div>
          <label>登录密码</label>
          <div class="input-wrap"><span>●</span><input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入登录密码"></div>
          <button class="primary-btn" type="submit" :disabled="submitting">{{ submitting ? '正在登录...' : '进入系统' }}</button>
        </form>
        <div v-if="role === 'PUBLIC'" class="auth-footer">还没有账号？<router-link to="/register">立即注册公众监督员</router-link></div>
        <div v-else class="demo-tip"><span>演示账号</span><b>{{ demoAccount }}</b><i></i><span>密码</span><b>123456</b></div>
      </div>
    </section>
  </main>
</template>

<script>
import { supervisorLogin } from '../api/supervisor.js'
import { gridLogin, staffLogin } from '../api/role.js'
import { preparePasswordEncryption } from '../api/security.js'

export default {
  name: 'HomeView',
  created() { preparePasswordEncryption().catch(() => {}) },
  data() {
    return {
      role: 'PUBLIC', form: { account: '', password: '' }, submitting: false,
      toast: { show: false, message: '' }, toastTimer: null,
      roles: [
        { value: 'PUBLIC', short: '公众监督员', icon: '公' },
        { value: 'GRID_MEMBER', short: '检测网格员', icon: '网' },
        { value: 'ADMIN', short: '系统管理员', icon: '管' },
        { value: 'DECISION', short: '决策者', icon: '决' }
      ]
    }
  },
  computed: {
    roleName() { return this.roles.find(item => item.value === this.role)?.short || '' },
    accountLabel() { return this.role === 'PUBLIC' ? '手机号码' : (this.role === 'GRID_MEMBER' ? '网格员编号 / 登录编码' : '登录账号') },
    accountPlaceholder() { return this.role === 'PUBLIC' ? '请输入 11 位手机号' : '请输入账号' },
    demoAccount() { return ({ GRID_MEMBER: 'GRID001', ADMIN: 'admin', DECISION: 'decision' })[this.role] || '' }
  },
  methods: {
    selectRole(role) { this.role = role; this.form = { account: '', password: '' } },
    showToast(message) {
      this.toast = { show: true, message }; clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => { this.toast.show = false }, 2600)
    },
    async handleLogin() {
      if (!this.form.account || !this.form.password) return this.showToast('请输入完整的账号和密码')
      if (this.role === 'PUBLIC' && !/^1[3-9]\d{9}$/.test(this.form.account)) return this.showToast('请输入正确的 11 位手机号')
      this.submitting = true
      try {
        let res
        if (this.role === 'PUBLIC') res = await supervisorLogin({ telId: this.form.account, password: this.form.password })
        else if (this.role === 'GRID_MEMBER') res = await gridLogin(this.form)
        else res = await staffLogin({ username: this.form.account, password: this.form.password, role: this.role })
        if (res.data.code !== 200) return this.showToast(res.data.message || '登录失败')
        this.$store.commit('setUser', { ...res.data.data, role: this.role })
        this.$router.push(({ PUBLIC: '/aqiFeedback', GRID_MEMBER: '/grid/tasks', ADMIN: '/admin', DECISION: '/decision' })[this.role])
      } catch (error) {
        this.showToast(error.response?.data?.message || '无法连接后端服务，请确认 9000 端口已启动')
      } finally { this.submitting = false }
    }
  }
}
</script>

<style scoped>
.auth-page{min-height:100vh;display:grid;grid-template-columns:1.08fr .92fr;background:radial-gradient(circle at 90% 8%,rgba(18,130,101,.09),transparent 28%),#f3f7f8}.intro-panel{display:flex;align-items:center;padding:clamp(55px,8vw,125px);color:#fff;background:linear-gradient(145deg,#062e32 0,#075849 48%,#0d5578 100%);position:relative;overflow:hidden}.intro-panel:before{content:'';position:absolute;inset:0;background-image:linear-gradient(rgba(255,255,255,.035) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.035) 1px,transparent 1px);background-size:42px 42px;mask-image:linear-gradient(to bottom right,#000,transparent 78%)}.intro-panel:after{content:'';position:absolute;width:650px;height:650px;border:1px solid rgba(255,255,255,.11);border-radius:50%;right:-270px;top:-250px;box-shadow:0 0 0 70px rgba(255,255,255,.025),0 0 0 140px rgba(255,255,255,.018)}.intro-content{position:relative;z-index:1;max-width:680px}.intro-brand{display:flex;align-items:center;gap:12px;margin-bottom:75px;color:rgba(255,255,255,.7);font-size:11px;font-weight:700;letter-spacing:1.7px}.intro-brand>i{position:relative;width:36px;height:36px;border:1px solid rgba(255,255,255,.25);border-radius:11px;background:rgba(255,255,255,.08)}.intro-brand>i:before,.intro-brand>i:after{content:'';position:absolute;width:8px;height:17px;border-radius:100% 0;background:#fff;transform:rotate(-28deg)}.intro-brand>i:before{left:9px;top:8px}.intro-brand>i:after{right:8px;bottom:7px}.eyebrow{display:inline-block;padding:8px 12px;border:1px solid rgba(255,255,255,.2);border-radius:999px;background:rgba(255,255,255,.06);font-size:11px;letter-spacing:1.8px}h1{margin:28px 0 18px;font-size:clamp(38px,4vw,62px);font-weight:760;line-height:1.24;letter-spacing:-1.5px;text-shadow:0 10px 32px rgba(0,0,0,.16)}.intro-content>p{max-width:620px;color:rgba(255,255,255,.72);font-size:16px;line-height:1.9}.role-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:9px;margin-top:40px}.role-summary span{display:flex;flex-direction:column;gap:8px;padding:13px 14px;border:1px solid rgba(255,255,255,.1);border-radius:11px;background:rgba(255,255,255,.07);font-size:12px;backdrop-filter:blur(8px)}.role-summary i{color:rgba(255,255,255,.45);font-size:9px;font-style:normal;letter-spacing:1px}.platform-state{display:flex;justify-content:space-between;margin-top:80px;padding-top:17px;border-top:1px solid rgba(255,255,255,.12);color:rgba(255,255,255,.5);font-size:10px;letter-spacing:.8px}.platform-state span{display:flex;align-items:center;gap:8px}.platform-state span i{width:7px;height:7px;border-radius:50%;background:#58d8a6;box-shadow:0 0 0 5px rgba(88,216,166,.1)}.auth-panel{display:flex;align-items:center;justify-content:center;padding:45px}.auth-card{position:relative;width:100%;max-width:520px;padding:44px;border:1px solid rgba(33,78,95,.11);border-radius:25px;background:rgba(255,255,255,.91);box-shadow:0 32px 90px rgba(26,57,72,.14);box-sizing:border-box;backdrop-filter:blur(18px)}.card-brand{display:flex;align-items:center;margin-bottom:36px;color:#123d52;font-size:13px;font-weight:900;letter-spacing:1.3px}.card-brand i{width:9px;height:9px;margin-right:8px;border-radius:50%;background:#078467;box-shadow:0 0 0 5px #e7f7f2}.card-brand small{margin-left:auto;color:#91a1ad;font-size:10px;font-weight:600;letter-spacing:.4px}.welcome{margin:0 0 6px;color:#088268;font-size:12px;font-weight:800;letter-spacing:1.2px}h2{margin:0;color:#123449;font-size:30px;letter-spacing:-.5px}.subheading{margin:9px 0 26px;color:#8292a0;font-size:13px}.role-tabs{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:25px}.role-tabs button{padding:11px 4px;border:1px solid #e1e8ec;border-radius:12px;background:#f8fafb;color:#6c7d8e;cursor:pointer}.role-tabs button:hover{transform:translateY(-2px);border-color:#bbdcd2;background:#f3faf7}.role-tabs b{display:grid;place-items:center;width:27px;height:27px;margin:0 auto 6px;border-radius:8px;background:#e8eef1;font-size:11px}.role-tabs span{font-size:11px}.role-tabs button.active{border-color:#75bbaa;background:linear-gradient(145deg,#e9f8f3,#edf7fb);color:#126d59;box-shadow:0 7px 18px rgba(24,126,100,.1)}.role-tabs button.active b{background:linear-gradient(135deg,#087f65,#1a7298);color:#fff;box-shadow:0 5px 12px rgba(13,113,89,.2)}label{display:block;margin:16px 0 8px;color:#344f61;font-size:13px;font-weight:700}.input-wrap{display:flex;align-items:center;height:51px;border:1px solid #dce5e9;border-radius:12px;background:#fafcfd;transition:.2s}.input-wrap:focus-within{border-color:#168a6d;background:#fff;box-shadow:0 0 0 4px rgba(17,137,106,.09)}.input-wrap span{width:44px;color:#91a1af;text-align:center;font-size:11px}.input-wrap input{flex:1;min-width:0;height:100%;padding-right:12px;border:0;outline:0;background:transparent;color:#17364a;font-size:14px}.primary-btn{width:100%;height:52px;margin-top:28px;border:0;border-radius:12px;color:#fff;font-size:15px;font-weight:700;letter-spacing:.5px;cursor:pointer}.auth-footer,.demo-tip{margin-top:23px;padding-top:19px;border-top:1px solid #edf1f3;color:#7b8997;text-align:center;font-size:12px}.auth-footer a{color:#08775e;font-weight:700;text-decoration:none}.demo-tip{display:flex;align-items:center;justify-content:center;gap:7px;padding:11px;border:0;border-radius:10px;background:#f3f8f8;color:#86949f}.demo-tip b{color:#31566a}.demo-tip i{width:1px;height:13px;margin:0 5px;background:#d6e1e3}.toast{position:fixed;top:24px;left:50%;z-index:1000;transform:translateX(-50%);padding:13px 20px;border:1px solid #f4caca;border-radius:11px;background:rgba(255,241,241,.94);color:#c74c4c;box-shadow:0 12px 32px rgba(33,61,87,.15);backdrop-filter:blur(12px)}.toast-enter-active,.toast-leave-active{transition:.25s}.toast-enter-from,.toast-leave-to{opacity:0;transform:translate(-50%,-12px)}@media(max-width:1050px){.role-summary{grid-template-columns:repeat(2,1fr)}}@media(max-width:900px){.auth-page{grid-template-columns:1fr}.intro-panel{display:none}.auth-panel{padding:18px}.auth-card{padding:30px 22px}.card-brand{margin-bottom:27px}.role-tabs{grid-template-columns:repeat(2,1fr)}}
</style>
