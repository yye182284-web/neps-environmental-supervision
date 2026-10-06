<template>
  <main class="workspace-page">
    <transition name="toast"><div v-if="message" class="toast">{{ message }}</div></transition>
    <section class="page-hero">
      <div><p>GRID INSPECTION</p><h1>我的检测任务</h1><span>查看管理员指派的监督任务，录入实测污染物数据并自动计算 AQI 等级。</span></div>
      <button @click="loadTasks">刷新任务</button>
    </section>
    <section class="metrics">
      <article><span>全部任务</span><strong>{{ tasks.length }}</strong></article>
      <article><span>待接单</span><strong>{{ count(1) }}</strong></article>
      <article><span>污染及以上</span><strong>{{ pollutionCount }}</strong></article>
      <article><span>已完成</span><strong>{{ count(3) }}</strong></article>
    </section>
    <section class="card">
      <header><div><h2>指派任务列表</h2><span>当前网格员：{{ user.realName || '-' }}</span></div></header>
      <div v-if="loading" class="empty">正在加载任务...</div>
      <div v-else-if="!tasks.length" class="empty">当前没有指派给你的检测任务</div>
      <div v-else class="table-wrap">
        <table><thead><tr><th>任务</th><th>区域与地址</th><th>公众反馈</th><th>预估等级</th><th>状态</th><th>操作</th></tr></thead>
          <tbody><tr v-for="item in pagedTasks" :key="item.afId">
            <td><b>#{{ item.afId }}</b><small>{{ item.assignDate }} {{ shortTime(item.assignTime) }}</small></td>
            <td><b>{{ item.provinceName }} · {{ item.cityName }}</b><small>{{ item.address }}</small></td>
            <td class="feedback">{{ item.information }}</td>
            <td><span class="grade">{{ gradeText(item.estimatedGrade) }}</span></td>
            <td><span class="status" :class="'s'+item.state">{{ stateText(item.state) }}</span></td>
            <td><button v-if="Number(item.state)===1" data-test="task-accept" class="link-btn accept" :disabled="acceptingId===item.afId" @click="accept(item)">{{ acceptingId===item.afId ? '接单处理中...' : '接单' }}</button><button v-else class="link-btn" @click="openTask(item)">{{ Number(item.state) === 3 ? '查看结果' : '录入实测' }}</button><small v-if="Number(item.state)===1">{{ deadlineText(item.acceptDeadline) }}</small></td>
          </tr></tbody>
        </table>
      </div>
      <footer v-if="totalPages > 1" class="pager"><button :disabled="currentPage===1" @click="currentPage--">上一页</button><span>第 {{ currentPage }} / {{ totalPages }} 页</span><button :disabled="currentPage===totalPages" @click="currentPage++">下一页</button></footer>
    </section>

    <div v-if="selected" class="modal-mask" @click.self="selected=null">
      <section class="modal">
        <header><div><p>任务 #{{ selected.afId }}</p><h2>{{ selected.provinceName }} {{ selected.cityName }} · {{ selected.address }}</h2></div><button @click="selected=null">×</button></header>
        <div class="report"><b>公众反馈描述</b><p>{{ selected.information }}</p></div>
        <div v-if="Number(selected.state) === 3" class="result-box">
          <strong>最终实测结果</strong>
          <div class="result-values">
            <span>SO₂ <b>{{ selected.so2Concentration }}</b><small>{{ gradeText(selected.so2LevelId) }}</small></span>
            <span>CO <b>{{ selected.coConcentration }}</b><small>{{ gradeText(selected.coLevelId) }}</small></span>
            <span>PM2.5 <b>{{ selected.pm25Concentration }}</b><small>{{ gradeText(selected.pm25LevelId) }}</small></span>
          </div>
          <span class="overall">综合 AQI：{{ gradeText(selected.overallLevelId) }} · {{ formatDate(selected.measuredAt) }}</span>
        </div>
        <form v-else @submit.prevent="submit">
          <h3>实测数据录入</h3>
          <section class="smart-plan">
            <div><b>现场检测辅助分析</b><span>根据反馈内容和预估等级整理检测重点与安全提示。</span></div>
            <button type="button" data-test="grid-ai-plan" :disabled="planLoading" @click="loadInspectionPlan">{{ planLoading ? '生成中...' : '生成检测方案' }}</button>
            <div v-if="inspectionPlan" class="plan-result"><strong>风险级别：{{ inspectionPlan.riskLevel }}</strong><ul><li v-for="item in inspectionPlan.checklist" :key="item">{{ item }}</li></ul></div>
          </section>
          <div class="field-grid">
            <label>SO₂（μg/m³）<input v-model.number="measure.so2Concentration" data-test="so2" type="number" min="0" step="0.01" required></label>
            <label>CO（mg/m³）<input v-model.number="measure.coConcentration" data-test="co" type="number" min="0" step="0.01" required></label>
            <label>PM2.5（μg/m³）<input v-model.number="measure.pm25Concentration" data-test="pm25" type="number" min="0" step="0.01" required></label>
          </div>
          <label>实际检测地址<input v-model="measure.detailAddress" required></label>
          <label>检测备注<textarea v-model="measure.remark" rows="3" placeholder="可填写现场情况"></textarea></label>
          <div class="actions"><button type="button" class="cancel" @click="selected=null">取消</button><button data-test="measurement-submit" class="submit" :disabled="submitting">{{ submitting ? '正在提交...' : '提交实测数据' }}</button></div>
        </form>
      </section>
    </div>
  </main>
</template>

<script>
import { getGridTasks, acceptTask, submitMeasurement, generateInspectionPlan } from '../api/role.js'
export default {
  name: 'GridTasks',
  data: () => ({ tasks: [], selected: null, loading: true, submitting: false, acceptingId: null, planLoading: false, inspectionPlan: null, message: '', currentPage: 1, pageSize: 8, measure: { so2Concentration: null, coConcentration: null, pm25Concentration: null, detailAddress: '', remark: '' } }),
  computed: {
    user() { return this.$store.getters.user || {} },
    totalPages() { return Math.max(1, Math.ceil(this.tasks.length / this.pageSize)) },
    pagedTasks() { const start=(this.currentPage-1)*this.pageSize; return this.tasks.slice(start,start+this.pageSize) },
    pollutionCount() { return this.tasks.filter(x=>Number(x.overallLevelId)>=3).length }
  },
  created() { this.loadTasks() },
  methods: {
    async loadTasks() {
      if (!this.user.gmId) { this.tasks = []; this.loading = false; return }
      this.loading = true
      try { const r = await getGridTasks(this.user.gmId); this.tasks = r.data.data || [] } finally { this.loading = false }
    },
    openTask(item) { this.selected = item; this.inspectionPlan = null; this.measure = { so2Concentration: null, coConcentration: null, pm25Concentration: null, detailAddress: item.address, remark: '' } },
    async accept(item) {
      if (this.acceptingId) return
      this.acceptingId = item.afId
      try { const r=await acceptTask({ afId:item.afId, gmId:String(this.user.gmId) }); if (r.data.code!==200) throw new Error(r.data.message); this.flash('接单成功，请及时完成现场检测'); await this.loadTasks() }
      catch(e) { this.flash(e.response?.data?.message || e.message || '接单失败'); await this.loadTasks() }
      finally { this.acceptingId = null }
    },
    async loadInspectionPlan() {
      this.planLoading = true
      try {
        const r = await generateInspectionPlan({ information: this.selected.information, estimatedGrade: this.selected.estimatedGrade, address: this.selected.address })
        this.inspectionPlan = r.data.data
        if (!this.measure.remark) this.measure.remark = this.inspectionPlan.suggestedRemark
      } catch (e) { this.flash(e.response?.data?.message || '检测方案生成失败') }
      finally { this.planLoading = false }
    },
    async submit() {
      this.submitting = true
      try {
        const r = await submitMeasurement({ ...this.measure, afId: this.selected.afId, gmId: String(this.user.gmId) })
        if (r.data.code !== 200) throw new Error(r.data.message)
        this.selected = null; this.flash('实测数据已保存为最终结果，任务已完成'); await this.loadTasks()
      } catch (e) { this.flash(e.response?.data?.message || e.message || '提交失败') } finally { this.submitting = false }
    },
    flash(text) { this.message = text; setTimeout(() => { this.message = '' }, 2600) },
    count(state) { return this.tasks.filter(x => Number(x.state) === state).length },
    shortTime(v) { return v ? String(v).slice(0, 5) : '' },
    formatDate(v) { return v ? String(v).replace('T',' ').slice(0,16) : '-' },
    gradeText(v) { return ({1:'一级 · 优',2:'二级 · 良',3:'三级 · 轻度污染',4:'四级 · 中度污染',5:'五级 · 重度污染',6:'六级 · 严重污染'})[Number(v)] || '未评级' },
    stateText(v) { return ({1:'待接单',2:'检测中',3:'已完成'})[Number(v)] || '未开始' },
    deadlineText(v) { return v ? '请在 '+String(v).replace('T',' ').slice(11,16)+' 前接单' : '30 分钟内接单' }
  }
}
</script>

<style scoped>
.workspace-page{min-height:calc(100vh - 68px);padding:38px clamp(18px,4vw,58px) 70px;background:linear-gradient(180deg,#edf7f3,#f4f7fb 320px);box-sizing:border-box}.page-hero,.metrics,.card{max-width:1440px;margin-left:auto;margin-right:auto}.page-hero{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:24px}.page-hero p{margin:0;color:#178069;font-size:12px;font-weight:800;letter-spacing:1.5px}.page-hero h1{margin:7px 0;color:#18364e;font-size:36px}.page-hero span{color:#718296}.page-hero button,.submit{padding:12px 20px;border:0;border-radius:10px;background:linear-gradient(135deg,#17836a,#2573a5);color:#fff;font-weight:700;cursor:pointer}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:20px}.metrics article{padding:19px;border:1px solid #e0e8ee;border-radius:14px;background:#fff}.metrics span{display:block;color:#748497;font-size:13px}.metrics strong{color:#18364e;font-size:28px}.card{overflow:hidden;border:1px solid #e0e7ed;border-radius:17px;background:#fff;box-shadow:0 15px 45px rgba(31,58,80,.08)}.card>header{padding:20px 24px;border-bottom:1px solid #e9eef3}.card h2{display:inline;margin:0 12px 0 0;color:#233d54;font-size:19px}.card header span{color:#8493a2;font-size:13px}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:14px;text-align:left;border-top:1px solid #edf1f4;color:#3d5265}th{border:0;background:#f7f9fb;color:#6c7d8e}td small{display:block;margin-top:5px;color:#8c9aa8}.feedback{max-width:300px}.grade,.status{display:inline-block;padding:5px 9px;border-radius:999px;background:#edf3f8;color:#527086;font-size:12px;font-weight:700}.s1{color:#9b6816;background:#fff4d9}.s2{color:#286c9b;background:#e7f3fb}.s3{color:#18745c;background:#e5f7f0}.link-btn{padding:7px 11px;border:0;border-radius:7px;background:#e8f7f2;color:#19755f;cursor:pointer}.empty{padding:70px;text-align:center;color:#8290a0}.pager{display:flex;justify-content:flex-end;align-items:center;gap:12px;padding:14px 20px;border-top:1px solid #edf1f4;color:#718296;font-size:12px}.pager button{padding:7px 12px;border:1px solid #dbe3ea;border-radius:7px;background:#fff;color:#326b63;cursor:pointer}.pager button:disabled{opacity:.4;cursor:not-allowed}.modal-mask{position:fixed;inset:0;z-index:500;display:grid;place-items:center;padding:20px;background:rgba(18,39,55,.45)}.modal{width:min(720px,100%);max-height:90vh;overflow:auto;border-radius:18px;background:#fff;box-shadow:0 25px 80px rgba(0,0,0,.25)}.modal>header{display:flex;justify-content:space-between;padding:22px 26px;border-bottom:1px solid #e9eef3}.modal header p{margin:0 0 5px;color:#178069;font-size:12px}.modal h2{margin:0;color:#233d54;font-size:19px}.modal header button{border:0;background:none;font-size:27px;color:#8a98a5;cursor:pointer}.report,.result-box,form{margin:22px 26px}.report{padding:16px;border-radius:10px;background:#f4f8fa;color:#53697c}.report p{margin:7px 0 0}.field-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}form label{display:block;margin:14px 0;color:#34495e;font-size:13px;font-weight:700}input,textarea{width:100%;margin-top:7px;padding:11px;border:1px solid #dce4ec;border-radius:9px;box-sizing:border-box;font:inherit;outline:0}input:focus,textarea:focus{border-color:#218d73;box-shadow:0 0 0 3px rgba(33,141,115,.1)}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:20px}.cancel{padding:11px 20px;border:1px solid #dce4ec;border-radius:9px;background:#fff;cursor:pointer}.result-box{display:block;padding:18px;border-radius:10px;background:#eaf8f2;color:#18745c}.result-values{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;margin:14px 0}.result-values span{padding:10px;border:1px solid #cfeadf;border-radius:8px;background:rgba(255,255,255,.7);font-size:12px}.result-values b,.result-values small{display:block;margin-top:4px}.result-values b{font-size:18px}.result-values small{color:#67877e}.result-box .overall{font-weight:700}.toast{position:fixed;top:84px;left:50%;z-index:999;transform:translateX(-50%);padding:12px 20px;border:1px solid #bce9da;border-radius:10px;background:#ebfaf5;color:#1d735f}@media(max-width:760px){.metrics{grid-template-columns:repeat(2,1fr)}.field-grid,.result-values{grid-template-columns:1fr}.page-hero{align-items:flex-start;gap:20px;flex-direction:column}}
.smart-plan{display:grid;grid-template-columns:1fr auto;gap:10px;margin:12px 0;padding:14px;border:1px solid #cde5dc;border-radius:11px;background:#f0faf6}.smart-plan span{display:block;margin-top:4px;color:#6c8179;font-size:12px}.smart-plan button{border:0;border-radius:8px;padding:9px 13px;background:#1d806a;color:#fff;font-weight:700;cursor:pointer}.plan-result{grid-column:1/-1;color:#42685e}.plan-result ul{margin:8px 0 0;padding-left:20px}.plan-result li{margin:4px 0;font-size:12px}
.link-btn.accept{background:#fff3d7;color:#9a6510}
</style>
