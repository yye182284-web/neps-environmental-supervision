<template>
  <main class="ai-page">
    <section class="ai-shell">
      <header class="ai-header">
        <div class="ai-heading">
          <span class="ai-logo">AI</span>
          <div>
            <span class="eyebrow">LOCAL RAG COPILOT</span>
            <h1>环境智能协作中心</h1>
            <p><i></i>{{ capabilities.model || 'Qwen3' }} · {{ capabilities.knowledgeSections || 0 }} 个本地知识主题 · 会话级隐私</p>
          </div>
        </div>
        <div class="header-actions">
          <button v-if="loading" class="stop-btn" @click="stopStream">停止生成</button>
          <button :disabled="loading || (!messages.length && !riskResult)" @click="handleClear">清空</button>
        </div>
      </header>

      <div class="workspace-tabs">
        <button :class="{ active: activeTool === 'chat' }" @click="activeTool = 'chat'">
          <b>智能问答</b><small>多轮知识检索与数据研判</small>
        </button>
        <button :class="{ active: activeTool === 'risk' }" @click="activeTool = 'risk'">
          <b>风险预研判</b><small>可解释评分与现场行动清单</small>
        </button>
      </div>

      <template v-if="activeTool === 'chat'">
        <div class="mode-bar">
          <button v-for="item in modes" :key="item.id" :class="{ active: mode === item.id }"
                  :title="item.description" @click="mode = item.id">{{ item.name }}</button>
          <span>回答会结合当前角色与系统只读汇总，不会修改业务数据</span>
        </div>

        <div ref="messagesRef" class="messages">
          <div v-if="messages.length === 0" class="welcome">
            <div class="welcome-icon"><span>✦</span></div>
            <span class="eyebrow">EVIDENCE-GROUNDED ASSISTANT</span>
            <h2>{{ welcomeTitle }}</h2>
            <p>从本地环境知识库检索依据，并按你的角色补充业务上下文与行动建议。</p>
            <div class="feature-chips">
              <span>本地知识检索</span><span>多轮上下文</span><span>模型故障降级</span><span>角色化建议</span>
            </div>
            <div class="quick-list">
              <button v-for="question in quickQuestions" :key="question" @click="sendQuick(question)">
                <span>↗</span>{{ question }}
              </button>
            </div>
          </div>

          <article v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
            <div class="avatar">{{ message.role === 'user' ? userInitial : 'AI' }}</div>
            <div class="message-body">
              <div class="message-meta">
                <b>{{ message.role === 'user' ? '我' : 'NEPS 智能助手' }}</b>
                <span v-if="message.role === 'assistant'">{{ modeName(message.mode) }}</span>
              </div>
              <div class="bubble"><span>{{ message.content }}</span><span v-if="message.streaming" class="cursor">▋</span></div>
            </div>
          </article>
        </div>

        <footer class="composer">
          <div class="input-wrap">
            <textarea v-model="input" rows="1" maxlength="600" :disabled="loading"
                      placeholder="输入环境质量、现场检测、任务调度或统计研判问题" @keydown="handleKeydown"></textarea>
            <span class="char-count">{{ input.length }}/600</span>
            <button class="send-btn" :disabled="loading || !input.trim()" @click="handleSend">
              <span v-if="loading" class="spinner"></span><span v-else>↑</span>
            </button>
          </div>
          <p>{{ capabilities.privacy || '智能结果仅供辅助，最终结论以仪器实测与现行规范为准。' }}</p>
        </footer>
      </template>

      <template v-else>
        <div class="risk-workbench">
          <section class="risk-input-panel">
            <span class="eyebrow">EXPLAINABLE TRIAGE</span>
            <h2>描述现场，生成派单前风险预研判</h2>
            <p>评分基于健康风险、排放现象、敏感场所、异常时段和地址完整度。所有命中依据都会展示。</p>
            <textarea v-model="riskInput" maxlength="1000"
                      placeholder="示例：今晚 22 点，某小学东侧化工园附近持续出现刺鼻异味和黑烟，有居民反映眼睛刺痛……"></textarea>
            <div class="risk-controls">
              <span>{{ riskInput.length }}/1000</span>
              <button :disabled="analyzing || !riskInput.trim()" @click="analyzeRisk">
                <span v-if="analyzing" class="spinner"></span><span v-else>开始研判</span>
              </button>
            </div>
            <div class="sample-list">
              <button @click="riskInput = '今晚22点，某小学东侧化工园附近持续出现刺鼻异味和大量黑烟，有居民反映眼睛刺痛。'">高风险示例</button>
              <button @click="riskInput = '建设路与和平街交叉口附近有少量道路扬尘，持续约十分钟。'">常规示例</button>
            </div>
          </section>

          <section v-if="riskResult" class="risk-result">
            <div class="score-card" :class="riskClass">
              <div><span>风险评分</span><strong>{{ riskResult.riskScore }}</strong><small>/ 100</small></div>
              <div><span>建议等级</span><b>{{ riskResult.riskLevel }}</b></div>
              <div><span>判断置信度</span><b>{{ riskResult.confidence }}%</b></div>
              <div><span>预估 AQI 级别</span><b>{{ riskResult.estimatedGrade }} 级</b></div>
            </div>
            <div class="result-grid">
              <article><h3>评分依据</h3><ul><li v-for="item in riskResult.triggers" :key="item">{{ item }}</li></ul></article>
              <article><h3>优先行动</h3><ol><li v-for="item in riskResult.actions" :key="item">{{ item }}</li></ol></article>
              <article class="wide"><h3>建议补充证据</h3><div class="checklist"><span v-for="item in riskResult.evidenceChecklist" :key="item">✓ {{ item }}</span></div></article>
            </div>
            <p class="disclaimer">{{ riskResult.disclaimer }}</p>
          </section>
          <section v-else class="risk-placeholder">
            <span>01</span><b>输入可观察事实</b><p>地点、时间、持续时长、颜色、气味、影响人群</p>
            <span>02</span><b>查看可解释评分</b><p>系统展示每个触发因素，不输出黑盒结论</p>
            <span>03</span><b>生成行动清单</b><p>辅助确定派单优先级，实测数据仍是最终依据</p>
          </section>
        </div>
      </template>
    </section>
  </main>
</template>

<script>
const STORAGE_PREFIX = 'neps-ai-chat-'

export default {
  name: 'AiChat',
  data() {
    return {
      activeTool: 'chat', input: '', loading: false, analyzing: false, controller: null,
      messages: [], mode: 'explain', riskInput: '', riskResult: null,
      conversationId: '', capabilities: { modes: [], suggestions: [] }
    }
  },
  computed: {
    userInitial() { return this.$store.getters.user?.realName?.slice(0, 1) || '我' },
    role() { return this.$store.getters.user?.role || 'PUBLIC' },
    modes() {
      return this.capabilities.modes?.length ? this.capabilities.modes : [
        { id: 'explain', name: '知识问答' }, { id: 'analysis', name: '数据研判' }, { id: 'action', name: '行动建议' }
      ]
    },
    quickQuestions() { return this.capabilities.suggestions?.length ? this.capabilities.suggestions : ['AQI 指数是如何分级的？'] },
    welcomeTitle() {
      return ({ PUBLIC: '把现场观察变成清晰、可核查的信息', GRID_MEMBER: '让每一次现场检测更有准备', ADMIN: '用数据和规则辅助任务调度', DECISION: '从监测数据中提炼治理重点' })[this.role]
    },
    storageKey() { return STORAGE_PREFIX + this.role },
    riskClass() {
      const level = this.riskResult?.riskLevel
      return level === '紧急' ? 'critical' : level === '高' ? 'high' : level === '中' ? 'medium' : 'normal'
    }
  },
  async mounted() { this.restoreSession(); await this.loadCapabilities() },
  beforeUnmount() { this.stopStream() },
  methods: {
    newConversationId() {
      return window.crypto?.randomUUID ? window.crypto.randomUUID() : `chat_${Date.now()}_${Math.random().toString(36).slice(2)}`
    },
    restoreSession() {
      const saved = JSON.parse(sessionStorage.getItem(this.storageKey) || 'null')
      this.conversationId = saved?.conversationId || this.newConversationId()
      this.messages = Array.isArray(saved?.messages) ? saved.messages.map(item => ({ ...item, streaming: false })) : []
    },
    saveSession() {
      sessionStorage.setItem(this.storageKey, JSON.stringify({ conversationId: this.conversationId, messages: this.messages.slice(-20) }))
    },
    async loadCapabilities() {
      try {
        const response = await fetch('/api/ai/capabilities', { credentials: 'include' })
        if (response.ok) this.capabilities = (await response.json()).data || this.capabilities
      } catch (e) { /* 默认能力仍可使用 */ }
    },
    handleKeydown(event) {
      if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); this.handleSend() }
    },
    sendQuick(question) { this.input = question; this.handleSend() },
    async handleSend() {
      const message = this.input.trim()
      if (!message || this.loading) return
      this.messages.push({ role: 'user', content: message, mode: this.mode })
      const reply = { role: 'assistant', content: '', streaming: true, mode: this.mode }
      this.messages.push(reply); this.input = ''; this.loading = true; this.scrollToBottom()
      try { await this.streamChat(message, reply) }
      catch (error) { reply.content = error.name === 'AbortError' ? '回答已停止。' : '智能服务暂时不可用，请稍后重试。' }
      finally { reply.streaming = false; this.loading = false; this.controller = null; this.saveSession(); this.scrollToBottom() }
    },
    async streamChat(message, reply) {
      this.controller = new AbortController()
      const timeout = window.setTimeout(() => this.controller?.abort(), 50000)
      try {
        const response = await fetch('/api/ai/chat', {
          method: 'POST', credentials: 'include', signal: this.controller.signal,
          headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
          body: JSON.stringify({ conversationId: this.conversationId, message, mode: this.mode })
        })
        if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`)
        const reader = response.body.getReader(); const decoder = new TextDecoder('utf-8'); let buffer = ''
        const consume = (flush = false) => {
          buffer = buffer.replace(/\r\n/g, '\n')
          const events = buffer.split('\n\n'); buffer = flush ? '' : events.pop()
          events.forEach(event => event.split('\n').filter(line => line.startsWith('data:')).forEach(line => { reply.content += line.slice(5).replace(/^ /, '') }))
          this.scrollToBottom()
        }
        while (true) {
          const { done, value } = await reader.read(); if (done) break
          buffer += decoder.decode(value, { stream: true }); consume()
        }
        buffer += decoder.decode(); consume(true)
      } finally { window.clearTimeout(timeout) }
    },
    stopStream() { if (this.controller) this.controller.abort() },
    async handleClear() {
      this.stopStream()
      try { await fetch(`/api/ai/conversations/${encodeURIComponent(this.conversationId)}`, { method: 'DELETE', credentials: 'include' }) } catch (e) { /* 本地仍清除 */ }
      this.messages = []; this.input = ''; this.riskResult = null; this.riskInput = ''; this.conversationId = this.newConversationId(); this.saveSession()
    },
    async analyzeRisk() {
      if (!this.riskInput.trim() || this.analyzing) return
      this.analyzing = true
      try {
        const response = await fetch('/api/ai/risk-assessment', {
          method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: this.riskInput.trim() })
        })
        const body = await response.json()
        if (!response.ok || body.code !== 200) throw new Error(body.message || '研判失败')
        this.riskResult = body.data
      } catch (error) { window.alert(error.message || '风险研判暂时不可用') } finally { this.analyzing = false }
    },
    modeName(id) { return this.modes.find(item => item.id === id)?.name || '知识问答' },
    scrollToBottom() { this.$nextTick(() => { if (this.$refs.messagesRef) this.$refs.messagesRef.scrollTop = this.$refs.messagesRef.scrollHeight }) }
  }
}
</script>

<style scoped>
.ai-page{min-height:calc(100vh - 73px);padding:26px;background:radial-gradient(circle at 80% 0,rgba(27,151,117,.13),transparent 30%),linear-gradient(145deg,#edf5f2,#f5f7fb 48%,#eaf2f6);box-sizing:border-box}.ai-shell{width:min(1180px,100%);height:calc(100vh - 125px);min-height:660px;margin:auto;display:flex;flex-direction:column;overflow:hidden;border:1px solid rgba(25,88,79,.1);border-radius:24px;background:rgba(255,255,255,.96);box-shadow:0 28px 80px rgba(24,61,75,.13)}.ai-header{display:flex;align-items:center;justify-content:space-between;padding:18px 24px;border-bottom:1px solid #e5edec;background:linear-gradient(120deg,#fbfefd,#f2faf7)}.ai-heading{display:flex;align-items:center;gap:14px}.ai-logo{display:grid;place-items:center;width:48px;height:48px;border-radius:15px;background:linear-gradient(145deg,#078467,#176f9f);color:#fff;font-weight:900;box-shadow:0 9px 24px rgba(7,132,103,.26)}.eyebrow{color:#16846d;font-size:10px;font-weight:900;letter-spacing:1.5px}.ai-heading h1{margin:2px 0 3px;color:#17384f;font-size:21px}.ai-heading p{display:flex;align-items:center;gap:7px;margin:0;color:#78909d;font-size:11px}.ai-heading p i{width:7px;height:7px;border-radius:50%;background:#24a77d;box-shadow:0 0 0 4px rgba(36,167,125,.1)}.header-actions{display:flex;gap:8px}.header-actions button{padding:9px 13px;border:1px solid #d9e4e5;border-radius:10px;background:#fff;color:#657c89;cursor:pointer}.header-actions .stop-btn{border-color:#f0c4c4;color:#c94b4b}.header-actions button:disabled{opacity:.42}.workspace-tabs{display:grid;grid-template-columns:1fr 1fr;padding:0 24px;border-bottom:1px solid #e7eeee;background:#fff}.workspace-tabs button{display:flex;align-items:baseline;gap:10px;padding:14px 4px;border:0;border-bottom:3px solid transparent;background:transparent;color:#7a8d98;text-align:left;cursor:pointer}.workspace-tabs button:first-child{margin-right:20px}.workspace-tabs button.active{border-bottom-color:#078467;color:#176854}.workspace-tabs b{font-size:14px}.workspace-tabs small{font-size:10px}.mode-bar{display:flex;align-items:center;gap:7px;padding:10px 24px;border-bottom:1px solid #e8eeee;background:#f8fbfa}.mode-bar button{padding:7px 11px;border:1px solid #dce7e5;border-radius:9px;background:#fff;color:#607682;cursor:pointer}.mode-bar button.active{border-color:#54ad95;background:#e9f7f2;color:#08735d;font-weight:700}.mode-bar>span{margin-left:auto;color:#93a2aa;font-size:10px}.messages{flex:1;overflow:auto;padding:24px 30px;background:linear-gradient(180deg,#f8fbfb,#f4f7f9);scroll-behavior:smooth}.welcome{max-width:850px;margin:28px auto;text-align:center}.welcome-icon{display:grid;place-items:center;width:58px;height:58px;margin:0 auto 14px;border-radius:19px;background:linear-gradient(145deg,#e4f6ef,#eaf3f8);color:#078467;font-size:27px}.welcome h2{margin:7px 0;color:#18384f;font-size:26px}.welcome>p{margin:0;color:#718696;font-size:13px}.feature-chips{display:flex;justify-content:center;gap:7px;flex-wrap:wrap;margin-top:17px}.feature-chips span{padding:5px 9px;border:1px solid #dbeae5;border-radius:99px;background:#f5fbf8;color:#4e756a;font-size:10px}.quick-list{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:23px}.quick-list button{display:flex;gap:9px;align-items:flex-start;padding:14px;border:1px solid #e0e9e9;border-radius:13px;background:#fff;color:#496170;text-align:left;line-height:1.45;cursor:pointer;transition:.2s}.quick-list button:hover{transform:translateY(-2px);border-color:#7bc6b3;box-shadow:0 8px 22px rgba(27,99,83,.09)}.quick-list button span{color:#078467;font-weight:900}.message{display:flex;max-width:86%;gap:11px;margin-bottom:20px}.message.user{margin-left:auto;flex-direction:row-reverse}.avatar{display:grid;place-items:center;width:35px;height:35px;flex:0 0 35px;border-radius:11px;background:linear-gradient(145deg,#078467,#176f9f);color:#fff;font-size:11px;font-weight:800}.assistant .avatar{background:#e2f2ed;color:#08785f}.message-body{min-width:0}.message-meta{display:flex;align-items:center;gap:8px;margin:0 0 6px;color:#59717e;font-size:11px}.message-meta span{padding:2px 6px;border-radius:6px;background:#eaf4f1;color:#2f7e68;font-size:9px}.user .message-meta{justify-content:flex-end}.bubble{padding:13px 16px;border:1px solid #e1e9eb;border-radius:4px 15px 15px;background:#fff;color:#304b5d;line-height:1.75;white-space:pre-wrap;word-break:break-word;box-shadow:0 4px 14px rgba(28,60,73,.04)}.user .bubble{border:0;border-radius:15px 4px 15px 15px;background:linear-gradient(145deg,#087f65,#137e92);color:#fff}.cursor{color:#20a982;animation:blink .8s infinite}.composer{padding:14px 22px 11px;border-top:1px solid #e6eded;background:#fff}.input-wrap{display:flex;align-items:flex-end;gap:9px;padding:7px 7px 7px 14px;border:1px solid #d9e5e4;border-radius:15px;background:#fbfdfd}.input-wrap:focus-within{border-color:#54ad95;box-shadow:0 0 0 4px rgba(7,132,103,.08)}.input-wrap textarea{flex:1;min-height:28px;max-height:100px;padding:7px 0;border:0;outline:0;resize:none;background:transparent;color:#294858;font:14px/1.55 inherit}.char-count{color:#a1adb3;font-size:9px}.send-btn{display:grid;place-items:center;width:42px;height:42px;border:0;border-radius:12px;background:linear-gradient(145deg,#078467,#176f9f);color:#fff;font-size:19px;cursor:pointer}.send-btn:disabled{opacity:.4}.composer>p{margin:7px 0 0;color:#9aa8b0;font-size:10px;text-align:center}.risk-workbench{flex:1;display:grid;grid-template-columns:minmax(320px,.8fr) minmax(480px,1.2fr);gap:0;min-height:0;background:#f5f8f9}.risk-input-panel{padding:34px;border-right:1px solid #e0e9e9;background:linear-gradient(160deg,#f9fdfb,#eef7f3)}.risk-input-panel h2{margin:7px 0 8px;color:#1d4052;font-size:23px;line-height:1.35}.risk-input-panel>p{margin:0 0 21px;color:#71848f;font-size:12px;line-height:1.7}.risk-input-panel textarea{width:100%;height:190px;padding:15px;box-sizing:border-box;border:1px solid #d7e5e1;border-radius:14px;outline:0;resize:vertical;background:#fff;color:#284a59;font:13px/1.7 inherit}.risk-input-panel textarea:focus{border-color:#58ad96;box-shadow:0 0 0 4px rgba(7,132,103,.07)}.risk-controls{display:flex;justify-content:space-between;align-items:center;margin-top:10px;color:#99a7ad;font-size:10px}.risk-controls button{min-width:105px;padding:11px 18px;border:0;border-radius:11px;background:linear-gradient(145deg,#087f65,#137e92);color:#fff;font-weight:700;cursor:pointer}.risk-controls button:disabled{opacity:.45}.sample-list{display:flex;gap:8px;margin-top:18px}.sample-list button{padding:7px 10px;border:1px solid #d5e4df;border-radius:9px;background:#fff;color:#5a776d;cursor:pointer;font-size:10px}.risk-result{overflow:auto;padding:27px}.score-card{display:grid;grid-template-columns:1.1fr repeat(3,1fr);gap:1px;overflow:hidden;border-radius:15px;background:#dfe8e8}.score-card>div{padding:16px;background:#fff}.score-card span,.score-card small{display:block;color:#84949c;font-size:10px}.score-card strong{color:#ca4747;font-size:38px;line-height:1}.score-card b{display:block;margin-top:9px;color:#274e5d;font-size:18px}.score-card.critical{box-shadow:0 0 0 2px #e75b5b}.score-card.high{box-shadow:0 0 0 2px #ef954e}.score-card.medium{box-shadow:0 0 0 2px #e4bd43}.score-card.normal{box-shadow:0 0 0 2px #55ad91}.result-grid{display:grid;grid-template-columns:1fr 1fr;gap:13px;margin-top:14px}.result-grid article{padding:17px;border:1px solid #e0e8e9;border-radius:13px;background:#fff}.result-grid article.wide{grid-column:1/-1}.result-grid h3{margin:0 0 10px;color:#254a5c;font-size:13px}.result-grid ul,.result-grid ol{margin:0;padding-left:19px;color:#58707c;font-size:11px;line-height:1.8}.checklist{display:flex;flex-wrap:wrap;gap:7px}.checklist span{padding:6px 9px;border-radius:8px;background:#eff7f4;color:#487466;font-size:10px}.disclaimer{color:#91a0a6;font-size:10px;text-align:center}.risk-placeholder{display:grid;grid-template-columns:45px 1fr;align-content:center;gap:6px 12px;padding:50px;color:#657b86}.risk-placeholder span{grid-row:span 2;display:grid;place-items:center;width:40px;height:40px;border-radius:12px;background:#e3f2ed;color:#188169;font-weight:900}.risk-placeholder b{align-self:end;color:#345565;font-size:13px}.risk-placeholder p{align-self:start;margin:0 0 26px;color:#91a0a8;font-size:11px}.spinner{width:14px;height:14px;border:2px solid rgba(255,255,255,.4);border-top-color:#fff;border-radius:50%;animation:spin .75s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@keyframes blink{50%{opacity:0}}@media(max-width:800px){.ai-page{padding:10px}.ai-shell{height:calc(100vh - 115px);min-height:600px;border-radius:16px}.ai-header{padding:14px}.ai-heading p,.mode-bar>span,.workspace-tabs small{display:none}.workspace-tabs{padding:0 14px}.mode-bar{padding:9px 14px}.messages{padding:17px}.quick-list{grid-template-columns:1fr}.message{max-width:95%}.risk-workbench{grid-template-columns:1fr;overflow:auto}.risk-input-panel{border-right:0;border-bottom:1px solid #e0e9e9;padding:22px}.score-card{grid-template-columns:1fr 1fr}.result-grid{grid-template-columns:1fr}.result-grid article.wide{grid-column:auto}.risk-placeholder{display:none}}
</style>
