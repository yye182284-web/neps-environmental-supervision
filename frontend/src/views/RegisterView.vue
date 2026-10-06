<template>
  <main class="register-page">
    <transition name="toast">
      <div v-if="toast.show" class="toast" :class="toast.type">{{ toast.message }}</div>
    </transition>

    <section class="register-card">
      <aside class="register-intro">
        <button class="back-link" type="button" @click="$router.push('/')">← 返回登录</button>
        <div>
          <span class="badge">NEPS</span>
          <h1>注册公众监督员</h1>
          <p>加入公众空气质量监督网络，共同记录身边的环境变化。</p>
        </div>
        <small>信息仅用于课程项目功能演示</small>
      </aside>

      <form class="register-form" @submit.prevent="handleRegister">
        <header>
          <p>创建账号</p>
          <h2>填写基本信息</h2>
        </header>

        <div class="form-grid">
          <div class="form-item">
            <label>手机号码 <i>*</i></label>
            <input v-model.trim="form.telId" maxlength="11" inputmode="numeric"
                   autocomplete="username" placeholder="请输入 11 位手机号" :class="{ invalid: errors.telId }">
            <small v-if="errors.telId">{{ errors.telId }}</small>
          </div>
          <div class="form-item">
            <label>真实姓名 <i>*</i></label>
            <input v-model.trim="form.realName" autocomplete="name"
                   placeholder="请输入真实姓名" :class="{ invalid: errors.realName }">
            <small v-if="errors.realName">{{ errors.realName }}</small>
          </div>
          <div class="form-item">
            <label>登录密码 <i>*</i></label>
            <input v-model="form.password" type="password" autocomplete="new-password"
                   placeholder="至少 6 位" :class="{ invalid: errors.password }">
            <small v-if="errors.password">{{ errors.password }}</small>
          </div>
          <div class="form-item">
            <label>确认密码 <i>*</i></label>
            <input v-model="form.confirmPassword" type="password" autocomplete="new-password"
                   placeholder="再次输入密码" :class="{ invalid: errors.confirmPassword }">
            <small v-if="errors.confirmPassword">{{ errors.confirmPassword }}</small>
          </div>
          <div class="form-item">
            <label>年龄 <i>*</i></label>
            <input v-model.number="form.age" type="number" min="1" max="120"
                   placeholder="请输入年龄" :class="{ invalid: errors.age }">
            <small v-if="errors.age">{{ errors.age }}</small>
          </div>
          <div class="form-item">
            <label>性别 <i>*</i></label>
            <div class="sex-options" :class="{ invalid: errors.sex }">
              <label><input v-model="form.sex" type="radio" :value="1"> 男</label>
              <label><input v-model="form.sex" type="radio" :value="0"> 女</label>
            </div>
            <small v-if="errors.sex">{{ errors.sex }}</small>
          </div>
          <div class="form-item full">
            <label>备注</label>
            <textarea v-model.trim="form.remarks" maxlength="100" rows="3" placeholder="选填，最多 100 字"></textarea>
          </div>
        </div>

        <div class="actions">
          <button type="button" class="secondary" @click="reset">重置</button>
          <button type="submit" class="primary" :disabled="submitting">
            {{ submitting ? '正在注册...' : '提交注册' }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<script>
import { supervisorRegister } from '../api/supervisor.js'

const emptyForm = () => ({
  telId: '', password: '', confirmPassword: '', realName: '', age: null, sex: null, remarks: ''
})

export default {
  name: 'RegisterView',
  data() {
    return {
      form: emptyForm(),
      errors: {},
      submitting: false,
      toast: { show: false, type: 'success', message: '' },
      toastTimer: null
    }
  },
  methods: {
    notify(type, message) {
      this.toast = { show: true, type, message }
      clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => { this.toast.show = false }, 2800)
    },
    validate() {
      const e = {}
      if (!/^1[3-9]\d{9}$/.test(this.form.telId)) e.telId = '手机号格式不正确'
      if (!this.form.realName) e.realName = '请输入真实姓名'
      if (!this.form.password || this.form.password.length < 6) e.password = '密码长度不能少于 6 位'
      if (this.form.confirmPassword !== this.form.password) e.confirmPassword = '两次输入的密码不一致'
      if (!Number.isInteger(this.form.age) || this.form.age < 1 || this.form.age > 120) e.age = '年龄必须在 1 到 120 岁之间'
      if (this.form.sex === null) e.sex = '请选择性别'
      this.errors = e
      return Object.keys(e).length === 0
    },
    reset() {
      this.form = emptyForm()
      this.errors = {}
    },
    async handleRegister() {
      if (!this.validate()) return this.notify('warning', '请完善注册信息')
      this.submitting = true
      try {
        const { confirmPassword, ...payload } = this.form
        const res = await supervisorRegister(payload)
        if (res.data.code !== 200) {
          this.notify('error', res.data.message || '注册失败')
          return
        }
        this.notify('success', '注册成功，即将返回登录页')
        setTimeout(() => this.$router.push('/'), 800)
      } catch (error) {
        this.notify('error', error.response?.data?.message || '无法连接后端服务，请确认 9000 端口已启动')
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.register-page { min-height: 100vh; display: grid; place-items: center; padding: 40px 24px; box-sizing: border-box; background: radial-gradient(circle at 10% 0, #dff3ed, transparent 30%), linear-gradient(145deg,#f4f8fb,#edf3f7); }
.register-card { display: grid; grid-template-columns: 310px minmax(0,680px); width: min(100%,990px); overflow: hidden; border: 1px solid #e2e9ef; border-radius: 24px; background: #fff; box-shadow: 0 25px 75px rgba(35,61,84,.14); }
.register-intro { display: flex; flex-direction: column; justify-content: space-between; min-height: 580px; padding: 34px; box-sizing: border-box; color: #fff; background: linear-gradient(160deg,#126650,#1e7890); }
.back-link { align-self: flex-start; padding: 0; border: 0; background: transparent; color: rgba(255,255,255,.78); cursor: pointer; }
.badge { display: inline-block; padding: 7px 11px; border: 1px solid rgba(255,255,255,.28); border-radius: 999px; font-size: 12px; font-weight: 800; letter-spacing: 1px; }
.register-intro h1 { margin: 20px 0 15px; font-size: 34px; line-height: 1.25; }
.register-intro p { color: rgba(255,255,255,.75); line-height: 1.8; }
.register-intro small { color: rgba(255,255,255,.55); }
.register-form { padding: 45px 50px; }
header p { margin: 0 0 5px; color: #178069; font-size: 13px; font-weight: 800; letter-spacing: 1px; }
header h2 { margin: 0 0 30px; color: #18344d; font-size: 27px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 19px 22px; }
.form-item { min-width: 0; }
.form-item.full { grid-column: 1 / -1; }
.form-item > label { display: block; margin-bottom: 8px; color: #385064; font-size: 14px; font-weight: 700; }
i { color: #e05d5d; font-style: normal; }
input, textarea, .sex-options { width: 100%; box-sizing: border-box; border: 1px solid #dce4ec; border-radius: 10px; background: #fbfcfe; color: #263e53; font: inherit; outline: 0; transition: .2s; }
input { height: 46px; padding: 0 13px; }
textarea { padding: 12px 13px; resize: vertical; }
input:focus, textarea:focus { border-color: #218d73; background: #fff; box-shadow: 0 0 0 4px rgba(33,141,115,.09); }
input.invalid, .sex-options.invalid { border-color: #e36a6a; }
.sex-options { display: flex; align-items: center; gap: 30px; height: 46px; padding: 0 14px; }
.sex-options label { display: flex; align-items: center; gap: 7px; cursor: pointer; }
.sex-options input { width: auto; height: auto; accent-color: #1a816a; }
.form-item small { display: block; margin-top: 5px; color: #d95757; }
.actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 28px; padding-top: 24px; border-top: 1px solid #edf1f5; }
.actions button { min-width: 112px; height: 45px; border-radius: 10px; font-weight: 700; cursor: pointer; }
.secondary { border: 1px solid #d8e0e8; background: #fff; color: #5b6d7e; }
.primary { border: 0; background: linear-gradient(135deg,#18836c,#2670a5); color: #fff; box-shadow: 0 8px 20px rgba(25,119,100,.2); }
.primary:disabled { opacity: .65; cursor: wait; }
.toast { position: fixed; top: 24px; left: 50%; z-index: 1000; transform: translateX(-50%); padding: 13px 20px; border: 1px solid #bce9da; border-radius: 10px; color: #1d735f; background: #ebfaf5; box-shadow: 0 10px 30px rgba(33,61,87,.15); }
.toast.error { color: #c74c4c; background: #fff1f1; border-color: #f4caca; }.toast.warning { color: #9d6818; background: #fff8e8; border-color: #f0dda9; }
.toast-enter-active,.toast-leave-active { transition: .25s; }.toast-enter-from,.toast-leave-to { opacity: 0; transform: translate(-50%,-10px); }
@media(max-width: 800px) { .register-card { grid-template-columns: 1fr; }.register-intro { min-height: auto; padding: 25px; }.register-intro div,.register-intro small { display:none; }.register-form { padding: 32px 25px; } }
@media(max-width: 560px) { .register-page { padding: 0; }.register-card { border-radius: 0; }.form-grid { grid-template-columns: 1fr; }.form-item.full { grid-column: auto; } }
</style>
