<template>
  <main class="page-shell">
    <transition name="toast">
      <div v-if="toast.show" class="toast" :class="toast.type">{{ toast.message }}</div>
    </transition>

    <section class="hero">
      <div>
        <p class="eyebrow">PUBLIC FEEDBACK</p>
        <h1>空气质量反馈</h1>
        <p class="hero-copy">查看公众监督反馈、处理进度与预估空气质量等级。</p>
      </div>
      <button class="primary-btn" @click="$router.push('/aqiFeedbackForm')"><span>＋</span> 新增反馈</button>
    </section>

    <section class="summary-grid">
      <article><span>反馈总数</span><strong>{{ list.length }}</strong><i>条</i></article>
      <article><span>待分配</span><strong>{{ countByState(0) }}</strong><i>条</i></article>
      <article><span>处理中</span><strong>{{ countByState(1) + countByState(2) }}</strong><i>条</i></article>
      <article><span>已处理</span><strong>{{ countByState(3) }}</strong><i>条</i></article>
    </section>

    <section class="content-card">
      <header class="toolbar">
        <div>
          <h2>反馈记录</h2>
          <span>共 {{ filteredList.length }} 条结果</span>
        </div>
        <div class="filters">
          <select v-model.number="stateFilter">
            <option :value="-1">全部状态</option>
            <option :value="0">未分配</option>
            <option :value="1">已分配</option>
            <option :value="2">处理中</option>
            <option :value="3">已处理</option>
          </select>
          <input v-model.trim="keyword" type="search" placeholder="搜索地址、城市或反馈内容">
          <button class="refresh-btn" :disabled="loading" @click="fetchList">刷新</button>
        </div>
      </header>

      <div v-if="loading" class="status-box"><span class="spinner"></span>正在读取反馈数据...</div>
      <div v-else-if="errorMessage" class="status-box error-box">
        <b>加载失败</b><span>{{ errorMessage }}</span><button @click="fetchList">重新加载</button>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th><th>反馈区域</th><th>详细地址</th><th>反馈时间</th>
              <th>预估等级</th><th>最终实测</th><th>处理状态</th><th>反馈内容</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedList" :key="item.afId">
              <td class="id-cell">#{{ item.afId }}</td>
              <td><b>{{ item.provinceName || '未知省份' }}</b><small>{{ item.cityName || '未知城市' }}</small></td>
              <td class="address">{{ item.address }}</td>
              <td><b>{{ item.afDate || '-' }}</b><small>{{ formatTime(item.afTime) }}</small></td>
              <td><span class="grade-tag" :class="'grade-' + Number(item.estimatedGrade || 0)">{{ getGradeText(item.estimatedGrade) }}</span></td>
              <td><span class="grade-tag" :class="'grade-' + Number(item.overallLevelId || 0)">{{ item.overallLevelId ? getGradeText(item.overallLevelId) : '待检测' }}</span></td>
              <td><span class="state-tag" :class="'state-' + Number(item.state || 0)"><i></i>{{ getStateText(item.state) }}</span></td>
              <td><span class="description" :title="item.information">{{ item.information || '-' }}</span></td>
              <td class="actions">
                <button class="edit" :disabled="Number(item.state) !== 0" :title="operationHint(item)" @click="$router.push('/aqiFeedbackForm?afId=' + item.afId)">修改</button>
                <button class="delete" :disabled="Number(item.state) !== 0" :title="operationHint(item)" @click="deleteItem(item.afId)">删除</button>
              </td>
            </tr>
            <tr v-if="filteredList.length === 0"><td colspan="9" class="empty">没有符合条件的反馈记录</td></tr>
          </tbody>
        </table>
      </div>
      <footer v-if="totalPages > 1" class="pager">
        <button :disabled="currentPage === 1" @click="currentPage--">上一页</button>
        <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button :disabled="currentPage === totalPages" @click="currentPage++">下一页</button>
      </footer>
    </section>
  </main>
</template>

<script>
import { getMyAqiFeedbackList, deleteByAfid } from '../api/aqiFeedback.js'

export default {
  name: 'AqiFeedBackList',
  data() {
    return {
      list: [], keyword: '', stateFilter: -1, loading: true, errorMessage: '', currentPage: 1, pageSize: 8,
      toast: { show: false, type: 'success', message: '' }, toastTimer: null
    }
  },
  computed: {
    filteredList() {
      const key = this.keyword.toLowerCase()
      return this.list.filter(item => {
        const stateMatch = this.stateFilter === -1 || Number(item.state || 0) === this.stateFilter
        const text = [item.provinceName, item.cityName, item.address, item.information, item.telId].join(' ').toLowerCase()
        return stateMatch && (!key || text.includes(key))
      })
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredList.length / this.pageSize)) },
    pagedList() { const start = (this.currentPage - 1) * this.pageSize; return this.filteredList.slice(start, start + this.pageSize) }
  },
  watch: {
    keyword() { this.currentPage = 1 },
    stateFilter() { this.currentPage = 1 }
  },
  created() { this.fetchList() },
  methods: {
    notify(type, message) {
      this.toast = { show: true, type, message }
      clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => { this.toast.show = false }, 2400)
    },
    async fetchList() {
      this.loading = true
      this.errorMessage = ''
      try {
        const res = await getMyAqiFeedbackList(this.$store.getters.user.telId)
        if (res.data.code !== 200) throw new Error(res.data.message || '查询失败')
        this.list = res.data.data || []
        if (this.currentPage > this.totalPages) this.currentPage = this.totalPages
      } catch (error) {
        this.errorMessage = error.response?.data?.message || error.message || '无法连接后端服务'
      } finally {
        this.loading = false
      }
    },
    async deleteItem(afId) {
      if (!window.confirm(`确定删除反馈 #${afId} 吗？`)) return
      try {
        const res = await deleteByAfid(afId)
        if (res.data.code === 200 && res.data.data) {
          this.notify('success', '删除成功')
          await this.fetchList()
        } else {
          this.notify('error', res.data.message || '删除失败')
        }
      } catch (error) {
        this.notify('error', '删除失败，请检查后端服务')
      }
    },
    countByState(state) { return this.list.filter(item => Number(item.state || 0) === state).length },
    getGradeText(grade) {
      return ({ 0: '未评级', 1: '一级·优', 2: '二级·良', 3: '三级·轻度', 4: '四级·中度', 5: '五级·重度', 6: '六级·严重' })[grade] || '未评级'
    },
    getStateText(state) {
      return ({ 0: '未分配', 1: '已分配', 2: '处理中', 3: '已处理' })[state] || '未分配'
    },
    formatTime(value) { return value ? String(value).slice(0, 5) : '-' },
    operationHint(item) { return Number(item.state) === 0 ? '' : '反馈已进入处理流程，不能修改或删除' }
  }
}
</script>

<style scoped>
.page-shell { min-height: calc(100vh - 68px); padding: 38px clamp(18px,4vw,58px) 60px; box-sizing: border-box; background: linear-gradient(180deg,#edf7f3 0,#f4f7fb 300px); }
.hero { max-width: 1440px; margin: 0 auto 25px; display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.eyebrow { margin: 0 0 7px; color: #178069; font-size: 12px; font-weight: 800; letter-spacing: 1.5px; }
h1 { margin: 0; color: #18364e; font-size: clamp(27px,3vw,38px); }.hero-copy { margin: 9px 0 0; color: #718296; }
.primary-btn { height: 46px; padding: 0 22px; border: 0; border-radius: 11px; background: linear-gradient(135deg,#17836a,#2573a5); color: #fff; font-weight: 700; cursor: pointer; box-shadow: 0 10px 22px rgba(26,112,96,.2); }
.summary-grid { max-width: 1440px; margin: 0 auto 22px; display: grid; grid-template-columns: repeat(4,1fr); gap: 14px; }
.summary-grid article { padding: 18px 20px; border: 1px solid #e0e8ee; border-radius: 14px; background: rgba(255,255,255,.9); box-shadow: 0 7px 22px rgba(35,61,84,.055); }
.summary-grid span { display: block; color: #748497; font-size: 13px; }.summary-grid strong { margin-right: 4px; color: #18364e; font-size: 27px; }.summary-grid i { color: #9aa7b4; font-size: 12px; font-style: normal; }
.content-card { max-width: 1440px; margin: 0 auto; overflow: hidden; border: 1px solid #e0e7ed; border-radius: 17px; background: #fff; box-shadow: 0 15px 45px rgba(31,58,80,.08); }
.toolbar { min-height: 79px; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; gap: 18px; border-bottom: 1px solid #e9eef3; }
.toolbar h2 { display: inline; margin: 0 9px 0 0; color: #233d54; font-size: 19px; }.toolbar span { color: #8493a2; font-size: 13px; }
.filters { display: flex; gap: 9px; }.filters input,.filters select,.refresh-btn { height: 38px; box-sizing: border-box; border: 1px solid #dbe3ea; border-radius: 9px; background: #fff; color: #455c70; }
.filters input { width: 245px; padding: 0 12px; }.filters select { padding: 0 28px 0 10px; }.refresh-btn { padding: 0 14px; cursor: pointer; }
.table-wrap { overflow-x: auto; }table { width: 100%; border-collapse: collapse; font-size: 13px; }th { padding: 13px 14px; background: #f7f9fb; color: #6c7d8e; text-align: left; font-weight: 700; white-space: nowrap; }
td { padding: 15px 14px; border-top: 1px solid #edf1f4; color: #3d5265; vertical-align: middle; }tbody tr:hover { background: #f8fbfa; }td small { display: block; margin-top: 5px; color: #8c9aa8; }.id-cell { color: #20806b; font-weight: 800; }.address { min-width: 150px; }
.description { display: block; max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.grade-tag,.state-tag { display: inline-flex; align-items: center; padding: 5px 9px; border-radius: 999px; white-space: nowrap; font-size: 12px; font-weight: 700; }
.grade-0 { color:#718096;background:#edf1f5}.grade-1{color:#157052;background:#e3f7ed}.grade-2{color:#577316;background:#f0f7d6}.grade-3{color:#a46600;background:#fff1cd}.grade-4{color:#bf4b2d;background:#ffebe4}.grade-5{color:#8a3479;background:#f7e5f3}.grade-6{color:#812d3c;background:#f5dfe4}
.state-tag { background:#edf3f8;color:#527086}.state-tag i { width:6px;height:6px;margin-right:6px;border-radius:50%;background:currentColor}.state-1,.state-2{color:#a56b12;background:#fff4d9}.state-3{color:#18745c;background:#e5f7f0}
  .actions { white-space: nowrap; }.actions button { padding: 6px 9px; border: 0; border-radius: 7px; background: transparent; cursor: pointer; }.actions button:disabled{color:#aab4bd;background:#f4f6f8;cursor:not-allowed}.edit { color:#19755f}.edit:hover:not(:disabled){background:#e8f7f2}.delete{color:#cf5656}.delete:hover:not(:disabled){background:#fff0f0}
.locked{color:#8b99a5;font-size:12px}
.empty,.status-box { padding: 65px 20px; text-align: center; color: #7d8d9d; }.status-box { display:flex;align-items:center;justify-content:center;gap:10px}.error-box { flex-direction:column;color:#b94f4f}.error-box button{padding:7px 14px;border:1px solid #e5b7b7;border-radius:7px;background:#fff;cursor:pointer}.spinner{width:18px;height:18px;border:2px solid #dce5e9;border-top-color:#19806a;border-radius:50%;animation:spin .8s linear infinite}
.pager{display:flex;justify-content:flex-end;align-items:center;gap:12px;padding:14px 20px;border-top:1px solid #edf1f4;color:#718296;font-size:12px}.pager button{padding:7px 12px;border:1px solid #dbe3ea;border-radius:7px;background:#fff;color:#326b63;cursor:pointer}.pager button:disabled{opacity:.4;cursor:not-allowed}
.toast { position:fixed;top:84px;left:50%;z-index:999;transform:translateX(-50%);padding:12px 20px;border:1px solid #bce9da;border-radius:10px;background:#ebfaf5;color:#1d735f;box-shadow:0 9px 25px rgba(30,60,80,.15)}.toast.error{color:#c74c4c;background:#fff1f1;border-color:#f4caca}.toast-enter-active,.toast-leave-active{transition:.25s}.toast-enter-from,.toast-leave-to{opacity:0;transform:translate(-50%,-10px)}
@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:900px){.summary-grid{grid-template-columns:repeat(2,1fr)}.toolbar{align-items:flex-start;flex-direction:column;padding:18px}.filters{width:100%;flex-wrap:wrap}.filters input{flex:1}.hero{align-items:flex-start;flex-direction:column}.primary-btn{align-self:stretch}}
@media(max-width:520px){.summary-grid{grid-template-columns:1fr 1fr}.page-shell{padding:25px 12px 45px}.filters input{width:100%;flex-basis:100%}}
</style>
