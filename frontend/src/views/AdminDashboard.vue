<template>
  <main class="admin-page">
    <transition name="toast"><div v-if="message" class="toast">{{ message }}</div></transition>
    <section class="hero"><div><p>SYSTEM ADMINISTRATION</p><h1>系统管理工作台</h1><span>公众反馈查询、任务属地化指派、网格员状态与最终实测数据管理。</span></div><button :disabled="loading" @click="refresh">{{ loading ? '刷新中...' : '刷新数据' }}</button></section>
    <section class="metrics">
      <article><span>公众反馈</span><strong>{{ overview.feedbackTotal || 0 }}</strong></article>
      <article><span>未指派</span><strong>{{ overview.unassignedTotal || 0 }}</strong></article>
      <article><span>处理中</span><strong>{{ overview.processingTotal || 0 }}</strong></article>
      <article><span>最终实测结果</span><strong>{{ measurementTotal }}</strong></article>
    </section>

    <section class="card">
      <header><div><h2>公众监督反馈与任务指派</h2><span>共 {{ feedbackTotal }} 条</span></div></header>
      <div class="filters">
        <input v-model.trim="feedbackFilter.keyword" placeholder="手机号、地址或反馈内容">
        <select v-model="feedbackFilter.state"><option value="">全部状态</option><option value="0">未指派</option><option value="1">已指派</option><option value="3">已完成</option></select>
        <select v-model="feedbackFilter.provinceId" @change="feedbackProvinceChanged"><option value="">全部省份</option><option v-for="p in provinces" :key="p.provinceId" :value="p.provinceId">{{ p.provinceName }}</option></select>
        <select v-model="feedbackFilter.cityId"><option value="">全部城市</option><option v-for="c in feedbackCities" :key="c.cityId" :value="c.cityId">{{ c.cityName }}</option></select>
        <input v-model="feedbackFilter.startDate" type="date" title="起始日期"><input v-model="feedbackFilter.endDate" type="date" title="结束日期">
        <button @click="searchFeedback">查询</button><button class="ghost" @click="resetFeedback">重置</button>
      </div>
      <div class="table-wrap"><table><thead><tr><th>编号</th><th>反馈区域</th><th>反馈内容</th><th>预估等级</th><th>状态</th><th>操作与指派</th></tr></thead>
        <tbody><tr v-for="item in feedback" :key="item.afId">
          <td><b>#{{ item.afId }}</b><small>{{ item.telId }}</small></td><td><b>{{ item.provinceName }} · {{ item.cityName }}</b><small>{{ item.address }}</small></td>
          <td class="description">{{ item.information }}</td><td>{{ gradeText(item.estimatedGrade) }}</td><td><span class="status" :class="'s'+item.state">{{ stateText(item.state) }}</span></td>
          <td><div class="operation"><button class="detail" @click="selectedFeedback=item">详情</button><div v-if="Number(item.state) === 0 && !Number(item.hasStaffingRequest)" class="assign"><select v-model="assignments[item.afId]"><option value="">{{ candidateMembers(item).length?'自动选择本地网格员':'本地暂无网格员' }}</option><option v-for="(m,index) in candidateMembers(item)" :key="m.gmId" :value="String(m.gmId)">{{ index===0?'推荐 · ':'' }}{{ m.gmName }}（本市 · {{ m.currentTaskCount||0 }}项）</option></select><button class="ai-btn" data-test="admin-ai-advice" :disabled="advisingId===item.afId" @click="advise(item)">{{ advisingId===item.afId?'分析中...':'智能调度' }}</button><button data-test="admin-assign" :disabled="assigningId===item.afId" @click="assign(item)">{{ assigningId===item.afId?'提交中...':(candidateMembers(item).length?'属地指派':'提交增配申请') }}</button></div><span v-else-if="Number(item.state)===0 && Number(item.hasStaffingRequest)" class="request-sent">✓ 增配申请已提交，等待决策</span><small v-if="dispatchAdvice[item.afId]" class="advice">{{ dispatchAdvice[item.afId] }}</small><span v-else-if="Number(item.state)>0" class="done">{{ Number(item.state)===1?'等待网格员接单':'已进入现场检测流程' }}</span></div></td>
        </tr><tr v-if="!feedback.length"><td colspan="6" class="empty">没有符合条件的反馈</td></tr></tbody>
      </table></div>
      <Pager v-if="feedbackPages>1" :page="feedbackPage" :pages="feedbackPages" @change="feedbackPage=$event;loadFeedback()" />
    </section>

    <section class="card">
      <header><div><h2>最终实测数据</h2><span>网格员提交后直接生效，共 {{ measurementTotal }} 条</span></div></header>
      <div class="filters">
        <input v-model.trim="measurementFilter.keyword" placeholder="地址、反馈内容或网格员">
        <select v-model="measurementFilter.levelId"><option value="">全部等级</option><option v-for="n in 6" :key="n" :value="n">{{ gradeText(n) }}</option></select>
        <select v-model="measurementFilter.provinceId" @change="measurementProvinceChanged"><option value="">全部省份</option><option v-for="p in provinces" :key="p.provinceId" :value="p.provinceId">{{ p.provinceName }}</option></select>
        <select v-model="measurementFilter.cityId"><option value="">全部城市</option><option v-for="c in measurementCities" :key="c.cityId" :value="c.cityId">{{ c.cityName }}</option></select>
        <input v-model="measurementFilter.startDate" type="date"><input v-model="measurementFilter.endDate" type="date">
        <button @click="searchMeasurements">查询</button><button class="ghost" @click="resetMeasurements">重置</button>
      </div>
      <div v-if="!measurements.length" class="empty">暂无符合条件的实测数据</div>
      <div v-else class="review-grid"><article v-for="item in measurements" :key="item.measurementId">
        <div class="review-title"><div><b>任务 #{{ item.afId }}</b><small>{{ item.provinceName }} {{ item.cityName }} · {{ item.gmName }}</small></div><span>{{ gradeText(item.overallLevelId) }}</span></div>
        <p>{{ item.address }}</p><div class="values"><span>SO₂ <b>{{ item.so2Concentration }}</b><small>{{ gradeText(item.so2LevelId) }}</small></span><span>CO <b>{{ item.coConcentration }}</b><small>{{ gradeText(item.coLevelId) }}</small></span><span>PM2.5 <b>{{ item.pm25Concentration }}</b><small>{{ gradeText(item.pm25LevelId) }}</small></span></div>
        <div class="final-mark">✓ 最终检测结果 · {{ formatDate(item.measuredAt) }}</div>
      </article></div>
      <Pager v-if="measurementPages>1" :page="measurementPage" :pages="measurementPages" @change="measurementPage=$event;loadMeasurements()" />
    </section>

    <section class="two-column">
      <article class="card compact"><header><div><h2>网格员工作状态</h2><span>来自网格员人事状态数据</span></div></header><div class="table-wrap"><table><thead><tr><th>网格员</th><th>区域</th><th>联系电话</th><th>状态</th></tr></thead><tbody><tr v-for="m in workforce" :key="m.gmId"><td><b>{{ m.gmName }}</b><small>{{ m.gmCode }}</small></td><td>{{ regionName(m) }}</td><td>{{ m.tel || '-' }}</td><td><span class="status" :class="'work-'+m.state">{{ workState(m.state) }}</span></td></tr></tbody></table></div></article>
      <article class="card compact"><header><div><h2>登录审计日志</h2><span>管理员登录行为留痕</span></div></header><div class="table-wrap"><table><thead><tr><th>账号</th><th>角色</th><th>结果</th><th>时间</th></tr></thead><tbody><tr v-for="log in loginLogs" :key="log.auditId"><td><b>{{ log.realName || log.accountName }}</b><small>{{ log.accountName }}</small></td><td>{{ roleName(log.accountType) }}</td><td><span class="status" :class="Number(log.success)?'s3':'s0'">{{ Number(log.success)?'成功':'失败' }}</span></td><td>{{ formatDate(log.loginTime) }}</td></tr></tbody></table></div></article>
    </section>

    <div v-if="selectedFeedback" class="modal-mask" @click.self="selectedFeedback=null"><section class="modal"><header><div><p>反馈详情</p><h2>任务 #{{ selectedFeedback.afId }}</h2></div><button @click="selectedFeedback=null">×</button></header><dl><dt>公众监督员</dt><dd>{{ selectedFeedback.telId }}</dd><dt>区域</dt><dd>{{ selectedFeedback.provinceName }} {{ selectedFeedback.cityName }}</dd><dt>详细地址</dt><dd>{{ selectedFeedback.address }}</dd><dt>反馈时间</dt><dd>{{ selectedFeedback.afDate }} {{ selectedFeedback.afTime }}</dd><dt>预估等级</dt><dd>{{ gradeText(selectedFeedback.estimatedGrade) }}</dd><dt>反馈描述</dt><dd>{{ selectedFeedback.information }}</dd></dl></section></div>
  </main>
</template>

<script>
import { queryAqiFeedback, getProvinces, getCityByPid } from '../api/aqiFeedback'
import { getAvailableMembers, assignTask, queryMeasurements, getStatisticsOverview, getGridMembers, getLoginLogs, getDispatchAdvice } from '../api/role'
import Pager from '../components/Pager.vue'

const emptyFeedbackFilter = () => ({ keyword:'', state:'', provinceId:'', cityId:'', startDate:'', endDate:'' })
const emptyMeasurementFilter = () => ({ keyword:'', levelId:'', provinceId:'', cityId:'', startDate:'', endDate:'' })

export default {
  name:'AdminDashboard', components:{ Pager },
  data:()=>({ loading:false, assigningId:null, advisingId:null, feedback:[], feedbackTotal:0, feedbackPage:1, feedbackPageSize:8, feedbackFilter:emptyFeedbackFilter(), feedbackCities:[], measurements:[], measurementTotal:0, measurementPage:1, measurementPageSize:6, measurementFilter:emptyMeasurementFilter(), measurementCities:[], members:[], workforce:[], provinces:[], overview:{}, assignments:{}, dispatchAdvice:{}, loginLogs:[], selectedFeedback:null, message:'' }),
  computed:{
    feedbackPages(){return Math.max(1,Math.ceil(this.feedbackTotal/this.feedbackPageSize))},
    measurementPages(){return Math.max(1,Math.ceil(this.measurementTotal/this.measurementPageSize))}
  },
  created(){this.refresh()},
  methods:{
    params(filter,page,size){const p={page,size};Object.entries(filter).forEach(([k,v])=>{if(v!==''&&v!==null)p[k]=v});return p},
    async refresh(){this.loading=true;try{const [p,m,w,s,l]=await Promise.all([getProvinces(),getAvailableMembers(),getGridMembers(),getStatisticsOverview(),getLoginLogs(20),this.loadFeedback(),this.loadMeasurements()]);this.provinces=p.data.data||[];this.members=m.data.data||[];this.workforce=w.data.data||[];this.overview=s.data.data||{};this.loginLogs=l.data.data||[];this.prepareAssignments()}catch(e){this.flash(e.response?.data?.message||'数据加载失败，请重新登录或检查后端服务')}finally{this.loading=false}},
    async loadFeedback(){const r=await queryAqiFeedback(this.params(this.feedbackFilter,this.feedbackPage,this.feedbackPageSize));const d=r.data.data||{};this.feedback=d.records||[];this.feedbackTotal=Number(d.total||0);this.feedback.forEach(item=>{if(Number(item.state)<=1&&!this.assignments[item.afId]){const first=this.candidateMembers(item)[0];if(first)this.assignments[item.afId]=String(first.gmId)}})},
    async loadMeasurements(){const r=await queryMeasurements(this.params(this.measurementFilter,this.measurementPage,this.measurementPageSize));const d=r.data.data||{};this.measurements=d.records||[];this.measurementTotal=Number(d.total||0)},
    searchFeedback(){this.feedbackPage=1;this.loadFeedback()}, resetFeedback(){this.feedbackFilter=emptyFeedbackFilter();this.feedbackCities=[];this.searchFeedback()},
    searchMeasurements(){this.measurementPage=1;this.loadMeasurements()}, resetMeasurements(){this.measurementFilter=emptyMeasurementFilter();this.measurementCities=[];this.searchMeasurements()},
    async feedbackProvinceChanged(){this.feedbackFilter.cityId='';this.feedbackCities=this.feedbackFilter.provinceId?(await getCityByPid(this.feedbackFilter.provinceId)).data.data||[]:[]},
    async measurementProvinceChanged(){this.measurementFilter.cityId='';this.measurementCities=this.measurementFilter.provinceId?(await getCityByPid(this.measurementFilter.provinceId)).data.data||[]:[]},
    async advise(item){this.advisingId=item.afId;try{const r=await getDispatchAdvice(item.afId);if(r.data.code!==200)throw new Error(r.data.message);const d=r.data.data||{};this.assignments[item.afId]=String(d.gmId);this.dispatchAdvice[item.afId]=d.reason;this.flash('已生成本地调度建议')}catch(e){this.flash(e.response?.data?.message||e.message||'智能调度失败')}finally{this.advisingId=null}},
    async assign(item){this.assigningId=item.afId;try{const gmId=this.assignments[item.afId]||null;const r=await assignTask({afId:item.afId,gmId});if(r.data.code===202){item.hasStaffingRequest=1;this.flash(r.data.message||'增配申请已提交给决策者');return}if(r.data.code!==200)throw new Error(r.data.message);this.flash('已按同市距离与当前工作量完成指派');const [m,w,s]=await Promise.all([getAvailableMembers(),getGridMembers(),getStatisticsOverview(),this.loadFeedback()]);this.members=m.data.data||[];this.workforce=w.data.data||[];this.overview=s.data.data||{}}catch(e){this.flash(e.response?.data?.message||e.message||'指派失败')}finally{this.assigningId=null}},
    prepareAssignments(){this.feedback.forEach(item=>{if(Number(item.state)<=1&&!this.assignments[item.afId]){const first=this.candidateMembers(item)[0];if(first)this.assignments[item.afId]=String(first.gmId)}})},
    candidateMembers(item){return this.members.filter(m=>Number(m.provinceId)===Number(item.provinceId)&&Number(m.cityId)===Number(item.cityId)).sort((a,b)=>Number(a.currentTaskCount||0)-Number(b.currentTaskCount||0)||String(a.lastAssignedAt||'').localeCompare(String(b.lastAssignedAt||'')))},
    regionName(m){return (m.provinceName||'未知省份')+' / '+(m.cityName||'未知城市')},
    workState(v){return({0:'可工作',1:'临时抽调',2:'休假',3:'其他'})[Number(v)]||'未知'}, roleName(v){return({PUBLIC:'公众监督员',GRID_MEMBER:'检测网格员',ADMIN:'管理员',DECISION:'决策者'})[v]||v},
    flash(t){this.message=t;setTimeout(()=>{this.message=''},2600)},
    gradeText(v){return({1:'一级·优',2:'二级·良',3:'三级·轻度',4:'四级·中度',5:'五级·重度',6:'六级·严重'})[Number(v)]||'未评级'},
    stateText(v){return({0:'待指派',1:'待接单',2:'检测中',3:'已完成'})[Number(v)]||'待指派'},
    formatDate(v){return v?String(v).replace('T',' ').slice(0,16):'-'}
  }
}
</script>

<style scoped>
.admin-page{min-height:calc(100vh - 68px);padding:38px clamp(18px,4vw,58px) 70px;background:linear-gradient(180deg,#edf7f3,#f4f7fb 320px);box-sizing:border-box}.hero,.metrics,.card,.two-column{max-width:1440px;margin-left:auto;margin-right:auto}.hero{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:24px}.hero p{margin:0;color:#178069;font-size:12px;font-weight:800;letter-spacing:1.5px}.hero h1{margin:7px 0;color:#18364e;font-size:36px}.hero span{color:#718296}.hero button,.filters button,.assign button{padding:10px 16px;border:0;border-radius:9px;background:linear-gradient(135deg,#17836a,#2573a5);color:#fff;font-weight:700;cursor:pointer}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:20px}.metrics article{padding:19px;border:1px solid #e0e8ee;border-radius:14px;background:#fff}.metrics span{display:block;color:#748497;font-size:13px}.metrics strong{color:#18364e;font-size:28px}.card{overflow:hidden;margin-bottom:20px;border:1px solid #e0e7ed;border-radius:17px;background:#fff;box-shadow:0 15px 45px rgba(31,58,80,.08)}.card>header{display:flex;justify-content:space-between;align-items:center;padding:19px 24px;border-bottom:1px solid #e9eef3}.card h2{display:inline;margin:0 10px 0 0;color:#233d54;font-size:19px}.card header span{color:#8493a2;font-size:13px}.filters{display:flex;gap:8px;flex-wrap:wrap;padding:14px 20px;background:#f8fafb;border-bottom:1px solid #e9eef3}.filters input,.filters select{height:38px;padding:0 10px;border:1px solid #dbe3ea;border-radius:8px;background:#fff;color:#455c70;box-sizing:border-box}.filters input:first-child{min-width:220px;flex:1}.filters .ghost{border:1px solid #ccd9df;background:#fff;color:#547080}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:14px;text-align:left;border-top:1px solid #edf1f4;color:#3d5265;vertical-align:middle}th{border:0;background:#f7f9fb;color:#6c7d8e}td small{display:block;margin-top:5px;color:#8c9aa8}.description{max-width:270px}.status{display:inline-block;padding:5px 9px;border-radius:999px;background:#edf3f8}.s1,.s2{background:#fff4d9;color:#9b6816}.s3,.work-0{background:#e5f7f0;color:#18745c}.s0,.work-3{background:#fff0f0;color:#bd5555}.work-1,.work-2{background:#fff4d9;color:#9b6816}.operation,.assign{display:flex;align-items:center;gap:7px}.assign select{max-width:245px;padding:7px;border:1px solid #dbe3ea;border-radius:7px}.assign button{padding:7px 10px;white-space:nowrap}.detail{padding:7px 10px;border:0;border-radius:7px;background:#edf5f8;color:#27687a;cursor:pointer}.done{color:#8493a2;font-size:12px;white-space:nowrap}.review-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:14px;padding:20px}.review-grid article{padding:18px;border:1px solid #e1e8ed;border-radius:13px;background:#fbfdfd}.review-title{display:flex;justify-content:space-between;gap:15px}.review-title small{display:block;margin-top:6px;color:#8290a0}.review-title>span{padding:5px 9px;border-radius:8px;background:#fff1cd;color:#9a6715;height:max-content}.review-grid p{color:#53697c}.values{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:14px 0}.values span{padding:9px;border-radius:8px;background:#edf5f4;color:#6a7c89;font-size:12px}.values b{display:block;margin-top:3px;color:#1b6f5e;font-size:16px}.final-mark{padding:9px;border-radius:8px;background:#e8f7f2;color:#18745c;text-align:center;font-size:12px;font-weight:700}.two-column{display:grid;grid-template-columns:1fr 1fr;gap:20px}.compact{margin:0}.empty{padding:45px;text-align:center;color:#8290a0}.toast{position:fixed;top:84px;left:50%;z-index:999;transform:translateX(-50%);padding:12px 20px;border:1px solid #bce9da;border-radius:10px;background:#ebfaf5;color:#1d735f}.modal-mask{position:fixed;inset:0;z-index:500;display:grid;place-items:center;padding:20px;background:rgba(18,39,55,.46)}.modal{width:min(620px,100%);border-radius:18px;background:#fff;box-shadow:0 25px 80px rgba(0,0,0,.25)}.modal>header{display:flex;justify-content:space-between;padding:21px 25px;border-bottom:1px solid #e9eef3}.modal header p{margin:0;color:#178069}.modal h2{margin:5px 0 0}.modal header button{border:0;background:none;font-size:25px;cursor:pointer}.modal dl{display:grid;grid-template-columns:105px 1fr;margin:0;padding:25px;gap:15px}.modal dt{color:#8795a2}.modal dd{margin:0;color:#344f61}@media(max-width:900px){.metrics,.two-column{grid-template-columns:repeat(2,1fr)}.hero{align-items:flex-start;gap:20px;flex-direction:column}.operation{align-items:flex-start;flex-direction:column}}@media(max-width:700px){.two-column{grid-template-columns:1fr}.metrics{grid-template-columns:repeat(2,1fr)}}
.assign .ai-btn{background:linear-gradient(135deg,#6c57c8,#397fc3)}.advice{display:block;max-width:520px;margin-top:7px;color:#566f89;line-height:1.5}
.request-sent{display:inline-flex;align-items:center;padding:8px 11px;border-radius:8px;background:#e7f7f1;color:#16715c;font-size:12px;font-weight:800;white-space:nowrap}
</style>
