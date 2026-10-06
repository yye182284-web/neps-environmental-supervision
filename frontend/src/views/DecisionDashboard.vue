<template>
  <main class="decision-page">
    <section class="hero">
      <div><p>DECISION ANALYTICS</p><h1>环境监测决策看板</h1><span>汇总公众监督、网格检测、区域风险与人员绩效，为环境治理提供决策依据。</span></div>
      <div class="hero-actions"><button data-test="decision-ai-summary" :disabled="analysisLoading" @click="analyze">{{ analysisLoading ? '研判中...' : '智能研判' }}</button><button :disabled="loading" @click="load(true)">{{ loading ? '更新中...' : '更新统计' }}</button></div>
    </section>

    <section class="metrics">
      <article><i>反馈</i><span>公众反馈总量</span><strong>{{ number(overview.feedbackTotal) }}</strong></article>
      <article><i>实测</i><span>最终实测总量</span><strong>{{ number(measurement.confirmedTotal) }}</strong></article>
      <article><i>优良</i><span>空气质量优良数</span><strong>{{ number(measurement.goodTotal) }}</strong></article>
      <article><i class="risk-icon">风险</i><span>污染及以上</span><strong>{{ number(measurement.pollutionTotal) }}</strong></article>
      <article><i>时效</i><span>平均完成用时</span><strong>{{ efficiency.averageCompletionHours ?? 0 }} <small>小时</small></strong></article>
    </section>

    <section class="risk-strip">
      <article><span>轻度以上风险</span><strong>{{ number(risk.highPollutionTotal) }}</strong><small>AQI 三级及以上</small></article>
      <article class="danger"><span>重度以上预警</span><strong>{{ number(risk.severePollutionTotal) }}</strong><small>AQI 五级及以上</small></article>
      <article><span>待指派任务</span><strong>{{ number(risk.unassignedTotal) }}</strong><small>建议及时安排检测</small></article>
      <article><span>网格覆盖率</span><strong>{{ number(measurement.provinceCoverageRate) }}% / {{ number(measurement.cityCoverageRate) }}%</strong><small>省份 {{ number(measurement.provinceCovered) }}/{{ number(measurement.provinceTotal) }} · 城市 {{ number(measurement.cityCovered) }}/{{ number(measurement.cityTotal) }}</small></article>
    </section>

    <section v-if="analysis" class="analysis-panel" data-test="decision-ai-result">
      <div><b>决策智能研判</b><p>{{ analysis.conclusion }}</p></div>
      <ol><li v-for="item in analysis.actions" :key="item">{{ item }}</li></ol>
    </section>

    <section class="workforce-panel">
      <header><div><p>人员配置决策</p><h2>网格员供需状态</h2></div><strong :class="{warn:workforce.needMore}">{{ workforce.recommendation || '正在分析人员配置...' }}</strong></header>
      <div class="workforce-metrics"><span>网格员总数<b>{{ number(workforce.total) }}</b></span><span>空闲<b>{{ number(workforce.idle) }}</b></span><span>忙碌<b>{{ number(workforce.busy) }}</b></span><span>不可用<b>{{ number(workforce.unavailable) }}</b></span><span>待决策增配申请<b>{{ number(workforce.pendingRequests) }}</b></span></div>
      <div v-if="staffingRequests.length" class="request-list"><article v-for="r in staffingRequests" :key="r.requestId"><div><b>{{ r.provinceName }} {{ r.cityName }}申请增配 {{ r.requestedCount }} 人</b><small>{{ r.reason }} · {{ formatDate(r.requestedAt) }}</small><small v-if="r.fulfilledMemberId" class="provisioned">已创建网格员 {{ r.fulfilledMemberId }}，可由管理员属地指派</small></div><span :class="'request-'+r.status">{{ requestStatus(r.status) }}</span><div v-if="r.status==='PENDING'" class="decision-buttons"><button :disabled="decidingId===r.requestId" @click="decide(r,true)">{{ decidingId===r.requestId?'处理中...':'同意' }}</button><button class="reject" :disabled="decidingId===r.requestId" @click="decide(r,false)">驳回</button></div></article></div>
    </section>

    <section class="comparison-grid">
      <article class="panel"><header><h2>年度污染环比</h2><span>前两年最终实测数据</span></header><div class="table-scroll"><div class="data-table yearly"><div class="tr head"><span>年份</span><span>实测量</span><span>反馈量</span><span>平均等级</span><span>污染率</span></div><div v-for="y in yearComparison" :key="y.year" class="tr"><b>{{ y.year }} 年</b><span>{{ y.measurementTotal }}</span><span>{{ y.feedbackTotal }}</span><span>{{ y.averageLevel }}</span><span class="pollution">{{ y.pollutionRate }}%</span></div></div></div></article>
      <article class="panel"><header><h2>地区污染治理研判</h2><span>污染严重、治理较好与同比变化</span></header><div class="table-scroll"><div class="data-table city-compare"><div class="tr head"><span>地区</span><span>上年/本年等级</span><span>变化</span><span>研判</span><span>治理意见</span></div><div v-for="c in cityComparison" :key="c.provinceName+c.cityName" class="tr"><b>{{ c.provinceName }} {{ c.cityName }}</b><span>{{ c.previousAverageLevel }} / {{ c.currentAverageLevel }}</span><span :class="Number(c.levelChange)>0?'pollution':'improve'">{{ signed(c.levelChange) }}</span><span>{{ c.assessment }}</span><span>{{ c.governanceAdvice }}</span></div></div></div></article>
      <article class="panel wide-panel"><header><h2>公众报告量与监督覆盖研判</h2><span>区分环境质量较好与监督员不足</span></header><div class="table-scroll"><div class="data-table report-compare"><div class="tr head"><span>地区</span><span>前年报告</span><span>去年报告</span><span>环比</span><span>监督员</span><span>污染率</span><span>结论</span></div><div v-for="r in reportComparison" :key="r.provinceName+r.cityName" class="tr"><b>{{ r.provinceName }} {{ r.cityName }}</b><span>{{ r.previousReportTotal }}</span><span>{{ r.currentReportTotal }}</span><span>{{ signed(r.reportChangeRate) }}%</span><span>{{ r.activeSupervisorTotal }}/{{ r.supervisorTotal }}</span><span>{{ r.pollutionRate || 0 }}%</span><span><em>{{ r.volumeAssessment }}</em>{{ r.reasonAssessment }}</span></div></div></div></article>
    </section>

    <section class="dashboard-grid">
      <article class="panel distribution">
        <header><h2>AQI 等级分布</h2><span>最终实测结果数量</span></header>
        <div v-for="item in distribution" :key="item.levelId" class="bar-row"><label>{{ item.levelName }} · {{ item.quality }}</label><div><i :style="{width:barWidth(item.value),background:item.color}"></i></div><b>{{ item.value }}</b></div>
      </article>

      <article class="panel">
        <header><h2>任务处理进度</h2><span>反馈处理状态</span></header>
        <div class="progress-ring" :style="{'--rate': completionRate+'%'}"><strong>{{ completionRate }}%</strong><span>完成率</span></div>
        <div class="legend"><span><i class="green"></i>已完成 {{ number(overview.completedTotal) }}</span><span><i class="gold"></i>处理中 {{ number(overview.processingTotal) }}</span><span><i class="gray"></i>未分配 {{ number(overview.unassignedTotal) }}</span></div>
      </article>

      <article class="panel wide-panel">
        <header><h2>污染风险预警</h2><span>三级及以上的最新实测记录</span></header>
        <div class="table-scroll"><div class="data-table alerts"><div class="tr head"><span>任务</span><span>区域</span><span>地址</span><span>实测等级</span><span>时间</span></div><div v-for="a in pagedAlerts" :key="a.afId" class="tr"><b>#{{ a.afId }}</b><span>{{ a.provinceName }} {{ a.cityName }}</span><span>{{ a.address }}</span><span><em :class="'level-'+a.overallLevelId">{{ gradeText(a.overallLevelId) }}</em></span><span>{{ formatDate(a.measuredAt) }}</span></div><div v-if="!alerts.length" class="no-data">暂无污染风险记录</div></div></div>
        <Pager v-if="alertPages > 1" :page="alertPage" :pages="alertPages" @change="alertPage=$event" />
      </article>

      <article class="panel province-panel">
        <header><h2>省域污染物与 AQI 超标统计</h2><span>按省份统计最终实测结果</span></header>
        <div class="table-scroll"><div class="data-table province-stats"><div class="tr head"><span>省份</span><span>反馈</span><span>实测</span><span>SO₂超标</span><span>CO超标</span><span>PM2.5超标</span><span>AQI超标</span></div><div v-for="p in pagedProvince" :key="p.provinceId" class="tr"><b>{{ p.provinceName }}</b><span>{{ p.feedbackTotal }}</span><span>{{ p.measurementTotal }}</span><span>{{ p.so2ExceedTotal }}</span><span>{{ p.coExceedTotal }}</span><span>{{ p.pm25ExceedTotal }}</span><span class="pollution">{{ p.aqiExceedTotal }}</span></div><div v-if="!province.length" class="no-data">暂无区域统计数据</div></div></div>
        <Pager v-if="provincePages > 1" :page="provincePage" :pages="provincePages" @change="provincePage=$event" />
      </article>

      <article class="panel city-panel">
        <header><h2>重点城市排名</h2><span>按污染数和反馈量排序</span></header>
        <div class="table-scroll"><div class="data-table"><div class="tr head"><span>城市</span><span>反馈量</span><span>实测量</span><span>污染数</span></div><div v-for="c in pagedCities" :key="c.cityId" class="tr"><b>{{ c.provinceName }} · {{ c.cityName }}</b><span>{{ c.feedbackTotal }}</span><span>{{ c.measurementTotal }}</span><span class="pollution">{{ c.pollutionTotal }}</span></div><div v-if="!cityRanking.length" class="no-data">暂无城市统计数据</div></div></div>
        <Pager v-if="cityPages > 1" :page="cityPage" :pages="cityPages" @change="cityPage=$event" />
      </article>

      <article class="panel wide-panel">
        <header><h2>网格员任务绩效</h2><span>用于了解区域检测负荷与完成情况</span></header>
        <div class="table-scroll"><div class="data-table performance"><div class="tr head"><span>网格员</span><span>负责区域</span><span>任务总数</span><span>完成数</span><span>平均等级</span><span>完成率</span></div><div v-for="g in pagedGrid" :key="g.gmId" class="tr"><b>{{ g.gmName }}<small>{{ g.gmCode }}</small></b><span>{{ g.provinceName || '-' }} {{ g.cityName || '' }}</span><span>{{ g.taskTotal }}</span><span>{{ g.completedTotal }}</span><span>{{ g.averageLevel || '-' }}</span><span>{{ rate(g.completedTotal,g.taskTotal) }}%</span></div><div v-if="!gridPerformance.length" class="no-data">暂无网格员统计数据</div></div></div>
        <Pager v-if="gridPages > 1" :page="gridPage" :pages="gridPages" @change="gridPage=$event" />
      </article>

      <article class="panel wide-panel">
        <header><h2>最新最终实测</h2><span>网格员提交后直接生效</span></header>
        <div class="table-scroll"><div class="data-table recent"><div class="tr head"><span>任务</span><span>区域 / 网格员</span><span>SO₂</span><span>CO</span><span>PM2.5</span><span>综合等级</span><span>实测时间</span></div><div v-for="m in pagedRecent" :key="m.measurementId" class="tr"><b>#{{ m.afId }}</b><span>{{ m.provinceName }} {{ m.cityName }}<small>{{ m.gmName || '-' }}</small></span><span>{{ m.so2Concentration }}</span><span>{{ m.coConcentration }}</span><span>{{ m.pm25Concentration }}</span><span>{{ gradeText(m.overallLevelId) }}</span><span>{{ formatDate(m.measuredAt) }}</span></div><div v-if="!recentMeasurements.length" class="no-data">暂无最终实测数据</div></div></div>
        <Pager v-if="recentPages > 1" :page="recentPage" :pages="recentPages" @change="recentPage=$event" />
      </article>

      <article class="panel trend-panel wide-panel">
        <header><h2>最近 12 个月 AQI 超标趋势</h2><span>柱顶为当月超标数，完整显示无数据月份</span></header>
        <div v-if="trend.length" class="trend-bars"><div v-for="t in trend" :key="t.month"><small>{{ t.pollutionTotal }}</small><b :style="{height:trendHeight(t.pollutionTotal)}" :title="`实测 ${t.measurementTotal} 条，累计超标 ${t.cumulativePollutionTotal} 条`"></b><span>{{ t.month.slice(5) }}月</span></div></div><div v-else class="no-data">产生实测记录后将显示月度趋势</div>
      </article>
    </section>
  </main>
</template>

<script>
import { getStatistics, getDecisionSummary, decideStaffingRequest } from '../api/role.js'
import Pager from '../components/Pager.vue'

export default {
  name: 'DecisionDashboard',
  components: { Pager },
  data: () => ({
    loading: false, analysisLoading: false, decidingId: null, analysis: null, overview: {}, measurement: {}, efficiency: {}, risk: {}, workforce: {}, yearComparison: [], cityComparison: [], reportComparison: [], staffingRequests: [], province: [], cityRanking: [], distribution: [], trend: [], gridPerformance: [], recentMeasurements: [],
    provincePage: 1, cityPage: 1, gridPage: 1, recentPage: 1, alertPage: 1, pageSize: 5
  }),
  computed: {
    completionRate() { return this.rate(this.overview.completedTotal, this.overview.feedbackTotal) },
    alerts() { return this.risk.alerts || [] },
    provincePages() { return this.pages(this.province) }, cityPages() { return this.pages(this.cityRanking) }, gridPages() { return this.pages(this.gridPerformance) }, recentPages() { return this.pages(this.recentMeasurements) }, alertPages() { return this.pages(this.alerts) },
    pagedProvince() { return this.slice(this.province, this.provincePage) }, pagedCities() { return this.slice(this.cityRanking, this.cityPage) }, pagedGrid() { return this.slice(this.gridPerformance, this.gridPage) }, pagedRecent() { return this.slice(this.recentMeasurements, this.recentPage) }, pagedAlerts() { return this.slice(this.alerts, this.alertPage) }
  },
  created() { this.restoreSnapshot(); this.load() },
  methods: {
    async analyze() {
      this.analysisLoading = true
      try { const response = await getDecisionSummary(); this.analysis = response.data.data || null }
      finally { this.analysisLoading = false }
    },
    restoreSnapshot() { try { const d=JSON.parse(sessionStorage.getItem('neps-decision-dashboard')||'null'); if(d)this.applyDashboard(d) } catch (_) { sessionStorage.removeItem('neps-decision-dashboard') } },
    applyDashboard(d) {
      this.overview=d.overview||{}; this.measurement=d.measurement||{}; this.efficiency=d.efficiency||{}; this.risk=d.risk||{}; this.workforce=d.workforce||{}; this.yearComparison=d.yearComparison||[]; this.cityComparison=d.cityComparison||[]; this.reportComparison=d.reportComparison||[]; this.staffingRequests=d.staffingRequests||[]
      this.province=d.province||[]; this.cityRanking=d.cityRanking||[]; this.distribution=d.distribution||[]; this.trend=d.trend||[]; this.gridPerformance=d.gridPerformance||[]; this.recentMeasurements=d.recentMeasurements||[]
    },
    async load(refresh = false) {
      this.loading = true
      try {
        const response = await getStatistics(refresh); const d = response.data.data || {}
        this.applyDashboard(d); sessionStorage.setItem('neps-decision-dashboard',JSON.stringify(d))
      } finally { this.loading = false }
    },
    number(v) { return Number(v || 0) }, rate(done,total) { const all=Number(total||0); return all ? Math.round(Number(done||0)*100/all) : 0 },
    pages(list) { return Math.max(1,Math.ceil(list.length/this.pageSize)) }, slice(list,page) { const start=(page-1)*this.pageSize; return list.slice(start,start+this.pageSize) },
    gradeText(v) { return ({1:'一级·优',2:'二级·良',3:'三级·轻度',4:'四级·中度',5:'五级·重度',6:'六级·严重'})[Number(v)] || '未评级' },
    formatDate(v) { return v ? String(v).replace('T',' ').slice(0,16) : '-' },
    signed(v) { const n=Number(v||0); return (n>0?'+':'')+n },
    requestStatus(v) { return ({PENDING:'待决策',APPROVED:'已同意',REJECTED:'已驳回'})[v] || v },
    async decide(item,approved) { this.decidingId=item.requestId; try { const response=await decideStaffingRequest(item.requestId,{approved,remark:approved?'同意增配本地网格员':'当前暂不增配'}); if(response.data.code!==200)throw new Error(response.data.message); item.status=approved?'APPROVED':'REJECTED'; item.fulfilledMemberId=response.data.data?.memberId||null; this.workforce.pendingRequests=Math.max(0,this.number(this.workforce.pendingRequests)-1); sessionStorage.removeItem('neps-decision-dashboard'); await this.load(true) } catch(e) { window.alert(e.response?.data?.message||e.message||'决策处理失败') } finally { this.decidingId=null } },
    barWidth(v) { const max=Math.max(1,...this.distribution.map(x=>Number(x.value))); return Math.max(Number(v)>0?6:0,Number(v)*100/max)+'%' },
    trendHeight(v) { const max=Math.max(1,...this.trend.map(x=>Number(x.pollutionTotal))); return Math.max(8,Number(v)*150/max)+'px' }
  }
}
</script>

<style scoped>
.decision-page{min-height:calc(100vh - 68px);padding:38px clamp(18px,4vw,58px) 70px;background:linear-gradient(145deg,#eaf6f2,#eef4f8 45%,#f5f7fb);box-sizing:border-box}.hero,.metrics,.risk-strip,.dashboard-grid{max-width:1440px;margin-left:auto;margin-right:auto}.hero{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:25px}.hero p{margin:0;color:#178069;font-size:12px;font-weight:800;letter-spacing:1.5px}.hero h1{margin:7px 0;color:#18364e;font-size:36px}.hero span{color:#718296}.hero button{padding:11px 18px;border:0;border-radius:9px;background:linear-gradient(135deg,#17836a,#2573a5);color:#fff;font-weight:700;cursor:pointer}.hero button:disabled{opacity:.55}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:14px}.metrics article,.risk-strip article{position:relative;padding:20px;border:1px solid #dce7e8;border-radius:15px;background:rgba(255,255,255,.94);box-shadow:0 10px 25px rgba(31,58,80,.06)}.metrics i{position:absolute;right:16px;top:16px;padding:6px 9px;border-radius:8px;background:#e8f5f1;color:#1b7b65;font-size:11px;font-style:normal}.metrics .risk-icon{background:#fff0e9;color:#c45f3d}.metrics span,.risk-strip span{display:block;color:#748497;font-size:13px}.metrics strong,.risk-strip strong{display:block;margin-top:7px;color:#18364e;font-size:29px}.metrics small{font-size:14px;color:#718296}.risk-strip{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:18px}.risk-strip article{padding:15px 18px;border-left:4px solid #2d8a76}.risk-strip article.danger{border-left-color:#d85858}.risk-strip strong{font-size:23px}.risk-strip small{color:#96a2ad;font-size:11px}.dashboard-grid{display:grid;grid-template-columns:1.2fr .8fr;gap:18px}.panel{overflow:hidden;padding:22px;border:1px solid #dfe8ec;border-radius:16px;background:#fff;box-shadow:0 14px 38px rgba(31,58,80,.07)}.wide-panel{grid-column:1/-1}.panel header{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.panel h2{margin:0;color:#233d54;font-size:18px}.panel header span{color:#8a99a8;font-size:12px}.bar-row{display:grid;grid-template-columns:135px 1fr 35px;align-items:center;gap:11px;margin:14px 0;color:#53697c;font-size:12px}.bar-row>div{height:10px;border-radius:999px;background:#edf1f3;overflow:hidden}.bar-row i{display:block;height:100%;border-radius:999px}.bar-row b{text-align:right}.progress-ring{width:160px;height:160px;margin:10px auto 22px;display:grid;place-content:center;text-align:center;border-radius:50%;background:conic-gradient(#19816b var(--rate),#e8eef1 0);position:relative}.progress-ring:before{content:'';position:absolute;inset:15px;border-radius:50%;background:#fff}.progress-ring strong,.progress-ring span{position:relative;z-index:1}.progress-ring strong{color:#1b6e5e;font-size:29px}.progress-ring span{color:#8290a0;font-size:12px}.legend{display:flex;justify-content:center;gap:15px;flex-wrap:wrap;color:#65788a;font-size:11px}.legend i{display:inline-block;width:7px;height:7px;margin-right:5px;border-radius:50%}.green{background:#19816b}.gold{background:#d3a23d}.gray{background:#aab5be}.table-scroll{overflow-x:auto}.data-table{min-width:560px}.data-table .tr{display:grid;grid-template-columns:1.4fr repeat(3,.7fr);align-items:center;padding:11px 8px;border-top:1px solid #edf1f3;color:#586c7e;font-size:12px}.data-table .head{border:0;background:#f5f8f9;color:#8795a2}.data-table small{display:block;margin-top:3px;color:#96a3ae}.performance .tr{grid-template-columns:1.2fr 1.4fr repeat(4,.65fr)}.recent .tr{grid-template-columns:.55fr 1.5fr repeat(4,.65fr) 1fr}.alerts .tr{grid-template-columns:.55fr 1.1fr 1.7fr 1fr 1fr}.pollution{color:#d2694e;font-weight:800}.alerts em{display:inline-block;padding:4px 7px;border-radius:7px;background:#fff0e9;color:#bc5e3f;font-style:normal;font-weight:700}.alerts .level-5,.alerts .level-6{background:#f8e5e8;color:#a83c50}.pager{display:flex;justify-content:flex-end;align-items:center;gap:11px;margin:14px -22px -22px;padding:13px 20px;border-top:1px solid #edf1f3;color:#718296;font-size:12px}.pager button{padding:6px 11px;border:1px solid #dbe3ea;border-radius:7px;background:#fff;color:#326b63;cursor:pointer}.pager button:disabled{opacity:.4;cursor:not-allowed}.trend-bars{height:190px;display:flex;align-items:flex-end;justify-content:space-around;gap:10px;padding-top:10px;border-bottom:1px solid #dce4e9}.trend-bars>div{height:100%;display:flex;flex-direction:column;justify-content:flex-end;align-items:center;gap:5px}.trend-bars b{width:30px;border-radius:7px 7px 0 0;background:linear-gradient(#2b9a7d,#2773a2)}.trend-bars span,.trend-bars small{font-size:10px;color:#7d8d9b}.trend-bars small{color:#1c725e;font-weight:700}.no-data{padding:55px 10px;text-align:center;color:#96a3ae}@media(max-width:900px){.metrics,.risk-strip{grid-template-columns:repeat(2,1fr)}.dashboard-grid{grid-template-columns:1fr}.wide-panel{grid-column:auto}.hero{align-items:flex-start;gap:20px;flex-direction:column}}@media(max-width:520px){.metrics,.risk-strip{grid-template-columns:1fr 1fr}.bar-row{grid-template-columns:105px 1fr 25px}}
.hero-actions{display:flex;gap:9px}.hero-actions button:first-child{background:linear-gradient(135deg,#6f56c9,#357fc0)}.analysis-panel{max-width:1440px;margin:0 auto 18px;padding:20px 23px;display:grid;grid-template-columns:1fr 1.4fr;gap:22px;border:1px solid #d9d7ef;border-radius:15px;background:linear-gradient(135deg,#f6f2ff,#f0f8fb);box-sizing:border-box}.analysis-panel b{color:#574ca0}.analysis-panel p{margin:7px 0 0;color:#485f73;line-height:1.6}.analysis-panel ol{margin:0;padding-left:22px;color:#50687a}.analysis-panel li{margin:5px 0}
.decision-page{overflow-x:hidden}.dashboard-grid,.panel{min-width:0}.metrics{grid-template-columns:repeat(4,minmax(0,1fr))}.metrics article,.risk-strip article{min-width:0}
@media(max-width:900px){.metrics,.risk-strip{grid-template-columns:repeat(2,minmax(0,1fr))}.metrics strong{font-size:clamp(21px,6vw,28px);white-space:nowrap}}
@media(max-width:520px){.metrics,.risk-strip{grid-template-columns:repeat(2,minmax(0,1fr))}}
.metrics{grid-template-columns:repeat(5,minmax(0,1fr))}.province-stats{min-width:800px}.province-stats .tr{grid-template-columns:1.3fr repeat(6,.75fr)}
.workforce-panel,.comparison-grid{max-width:1440px;margin:0 auto 18px}.workforce-panel{padding:22px;border:1px solid #d9e6e6;border-radius:16px;background:#fff;box-shadow:0 14px 38px rgba(31,58,80,.07)}.workforce-panel header{display:flex;justify-content:space-between;gap:20px;align-items:center}.workforce-panel header p{margin:0;color:#178069;font-size:11px;font-weight:800}.workforce-panel h2{margin:5px 0;color:#233d54}.workforce-panel header>strong{max-width:560px;color:#24725f;font-size:13px}.workforce-panel header>strong.warn{color:#b35f3f}.workforce-metrics{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin-top:18px}.workforce-metrics span{padding:13px;border-radius:10px;background:#f2f8f6;color:#6b7c8a;font-size:12px}.workforce-metrics b{display:block;margin-top:4px;color:#1e574e;font-size:22px}.request-list{margin-top:14px;border-top:1px solid #edf1f3}.request-list article{display:grid;grid-template-columns:1fr auto auto;align-items:center;gap:15px;padding:12px 3px;border-bottom:1px solid #edf1f3}.request-list small{display:block;margin-top:4px;color:#8493a2}.request-list>article>span{padding:5px 8px;border-radius:7px;background:#edf3f5;color:#617381;font-size:11px}.request-list .request-PENDING{background:#fff1d5;color:#9c6710}.decision-buttons{display:flex;gap:6px}.decision-buttons button{border:0;border-radius:7px;padding:7px 11px;background:#1c806a;color:#fff;cursor:pointer}.decision-buttons .reject{background:#eef2f4;color:#6b7a86}.comparison-grid{display:grid;grid-template-columns:.65fr 1.35fr;gap:18px}.yearly .tr{grid-template-columns:repeat(5,1fr)}.city-compare{min-width:900px}.city-compare .tr{grid-template-columns:1.1fr .8fr .5fr .7fr 2fr}.report-compare{min-width:1000px}.report-compare .tr{grid-template-columns:1.1fr repeat(5,.55fr) 2.5fr}.report-compare em{display:block;color:#276e60;font-style:normal;font-weight:800}.improve{color:#168269;font-weight:800}
.request-list small.provisioned{color:#168269;font-weight:700}
@media(max-width:1100px){.metrics{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(max-width:900px){.metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.comparison-grid{grid-template-columns:1fr}.workforce-metrics{grid-template-columns:repeat(2,1fr)}.workforce-panel header{align-items:flex-start;flex-direction:column}}
</style>
