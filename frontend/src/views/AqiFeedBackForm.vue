<template>
  <div class="form-page">
    <transition name="toast">
      <div v-if="toast.show" class="toast" :class="toast.type">
        <span class="toast-icon">{{ toastIcon }}</span>
        <span class="toast-msg">{{ toast.message }}</span>
      </div>
    </transition>

    <div class="form-card">
      <div class="form-header">
        <button class="back-btn" @click="goBack">
          <span class="back-arrow">&larr;</span> 返回列表
        </button>
        <h2 class="form-title">{{ form.afId ? '修改空气质量反馈' : '新增空气质量反馈' }}</h2>
      </div>

      <div v-if="loadingData" class="loading-wrap">
        <div class="spinner"></div>
        <span class="loading-text">数据加载中...</span>
      </div>

      <form v-else class="aqi-form" @submit.prevent="handleSubmit">
        <section v-if="!form.afId" class="smart-fill">
          <div class="smart-copy"><b>现场信息辅助录入</b><span>输入行政区划、道路门牌或地标及现场现象，系统将解析地址并整理规范反馈内容。</span></div>
          <div class="smart-control">
            <textarea v-model.trim="smartText" data-test="public-ai-text" rows="2" placeholder="例如：石家庄市长安区建设北大街38号工地附近出现明显扬尘和异味"></textarea>
            <button type="button" data-test="public-ai-fill" :disabled="smartLoading" @click="smartFill">{{ smartLoading ? '解析中...' : '解析并补全' }}</button>
          </div>
          <small v-if="smartExplanation">{{ smartExplanation }}</small>
        </section>
        <div class="form-row">
          <div class="form-item">
            <label class="form-label">手机号 <span class="required">*</span></label>
            <input
              type="text"
              v-model="form.telId"
              class="form-input"
              :class="{ 'has-error': errors.telId }"
              placeholder="请输入11位手机号"
              maxlength="11"
              :readonly="!form.afId"
            >
            <span v-if="errors.telId" class="error-msg">{{ errors.telId }}</span>
          </div>

          <div class="form-item">
            <label class="form-label">省份 <span class="required">*</span></label>
            <select
              v-model="form.provinceId"
              class="form-input"
              :class="{ 'has-error': errors.provinceId }"
            >
              <option v-for="item in provinces" :key="item.provinceId" :value="item.provinceId">
                {{ item.provinceName }}
              </option>
            </select>
            <span v-if="errors.provinceId" class="error-msg">{{ errors.provinceId }}</span>
          </div>
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">城市 <span class="required">*</span></label>
            <select
              v-model="form.cityId"
              class="form-input"
              :class="{ 'has-error': errors.cityId }"
              :disabled="cities.length === 0"
            >
              <option v-if="cities.length === 0" value="">请先选择省份</option>
              <option v-for="item in cities" :key="item.cityId" :value="item.cityId">
                {{ item.cityName }}
              </option>
            </select>
            <span v-if="errors.cityId" class="error-msg">{{ errors.cityId }}</span>
          </div>

          <div class="form-item">
            <label class="form-label">预估等级 <span class="required">*</span></label>
            <select
              v-model="form.estimatedGrade"
              class="form-input"
              :class="['grade-select', gradeClass(form.estimatedGrade)]"
            >
              <option :value="1">一级（优）</option>
              <option :value="2">二级（良）</option>
              <option :value="3">三级（轻度污染）</option>
              <option :value="4">四级（中度污染）</option>
              <option :value="5">五级（重度污染）</option>
              <option :value="6">六级（严重污染）</option>
            </select>
          </div>
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">反馈日期 <span class="required">*</span></label>
            <input
              type="date"
              v-model="form.afDate"
              class="form-input"
              :class="{ 'has-error': errors.afDate }"
            >
            <span v-if="errors.afDate" class="error-msg">{{ errors.afDate }}</span>
          </div>

          <div class="form-item">
            <label class="form-label">反馈时间 <span class="required">*</span></label>
            <input
              type="time"
              step="1"
              v-model="form.afTime"
              class="form-input"
              :class="{ 'has-error': errors.afTime }"
            >
            <span v-if="errors.afTime" class="error-msg">{{ errors.afTime }}</span>
          </div>
        </div>

        <div class="form-item full-width">
          <label class="form-label">详细地址 <span class="required">*</span></label>
          <input
            type="text"
            v-model="form.address"
            class="form-input"
            :class="{ 'has-error': errors.address }"
            placeholder="请输入详细地址"
          >
          <span v-if="errors.address" class="error-msg">{{ errors.address }}</span>
        </div>

        <div class="form-item full-width">
          <label class="form-label">反馈信息 <span class="required">*</span></label>
          <textarea
            v-model="form.information"
            class="form-input form-textarea"
            :class="{ 'has-error': errors.information }"
            placeholder="请详细描述空气质量情况..."
            rows="4"
          ></textarea>
          <span v-if="errors.information" class="error-msg">{{ errors.information }}</span>
        </div>

        <div class="form-actions">
          <button type="button" class="btn btn-default" @click="resetForm">重 置</button>
          <button type="submit" class="btn btn-primary" :disabled="submitting">
            <span v-if="submitting" class="btn-spinner"></span>
            {{ submitting ? '提交中...' : (form.afId ? '修 改' : '提 交') }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { saveAqiFeedback, getAqiFeedbackById, updateAqiFeedback, getProvinces, getCityByPid } from '../api/aqiFeedback.js'
import { generateFeedbackDraft } from '../api/role.js'

export default {
  name: 'AqiFeedBackForm',
  data() {
    return {
      form: {
        afId: null,
        telId: '',
        provinceId: null,
        cityId: null,
        address: '',
        afDate: '',
        afTime: '',
        information: '',
        estimatedGrade: 1
      },
      provinces: [],
      cities: [],
      errors: {},
      loadingData: true,
      submitting: false,
      smartText: '',
      smartLoading: false,
      smartExplanation: '',
      toast: {
        show: false,
        type: 'success',
        message: ''
      },
      toastTimer: null
    }
  },
  computed: {
    toastIcon() {
      const map = { success: '✓', error: '✕', warning: '!', info: 'i' }
      return map[this.toast.type] || 'i'
    }
  },
  async mounted() {
    try {
      await this.fetchProvinces()
      if (!this.form.afId) {
        if (this.$store.getters.user) {
          this.form.telId = this.$store.getters.user.telId
        }
        if (this.provinces.length > 0) {
          this.form.provinceId = this.provinces[0].provinceId
          await this.fetchCities(this.form.provinceId)
        }
      }

      const afId = this.$route.query.afId
      if (afId) {
        this.form.afId = afId
        await this.loadDetail(afId)
      }
    } catch (err) {
      this.showToast('error', '数据加载失败，请刷新重试')
    } finally {
      this.loadingData = false
    }
  },
  watch: {
    'form.provinceId'(newVal) {
      if (newVal) {
        this.fetchCities(newVal)
      }
    }
  },
  methods: {
    async smartFill() {
      if (!this.smartText) { this.showToast('warning', '请先用一句话描述现场情况'); return }
      this.smartLoading = true
      try {
        const res = await generateFeedbackDraft(this.smartText)
        const draft = res.data.data || {}
        if (draft.provinceId) {
          this.form.provinceId = Number(draft.provinceId)
          await this.fetchCities(this.form.provinceId)
        }
        if (draft.cityId && this.cities.some(c => Number(c.cityId) === Number(draft.cityId))) this.form.cityId = Number(draft.cityId)
        this.form.address = draft.address || this.form.address
        this.form.information = draft.information || this.form.information
        this.form.estimatedGrade = Number(draft.estimatedGrade || this.form.estimatedGrade)
        this.form.afDate = draft.afDate || this.form.afDate
        this.form.afTime = draft.afTime || this.form.afTime
        this.smartExplanation = draft.explanation || '信息解析完成，请核对详细地址后提交。'
        this.showToast('success', '已补全反馈信息')
      } catch (e) { this.showToast('error', e.response?.data?.message || '信息解析失败') }
      finally { this.smartLoading = false }
    },
    showToast(type, message) {
      this.toast = { show: true, type, message }
      if (this.toastTimer) clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => {
        this.toast.show = false
      }, 2500)
    },

    async fetchProvinces() {
      const res = await getProvinces()
      this.provinces = res.data.data || []
    },

    async fetchCities(provinceId) {
      const res = await getCityByPid(provinceId)
      this.cities = res.data.data || []
      if (!this.form.afId && this.cities.length > 0) {
        this.form.cityId = this.cities[0].cityId
      } else if (this.form.afId && this.form.cityId) {
        const exists = this.cities.some(c => c.cityId === this.form.cityId)
        if (!exists && this.cities.length > 0) {
          this.form.cityId = this.cities[0].cityId
        }
      }
    },

    async loadDetail(afId) {
      const res = await getAqiFeedbackById(afId)
      const detail = res.data.data
      Object.assign(this.form, {
        afId: detail.afId,
        telId: detail.telId,
        provinceId: detail.provinceId,
        cityId: detail.cityId,
        address: detail.address,
        afDate: detail.afDate,
        afTime: detail.afTime,
        information: detail.information,
        estimatedGrade: detail.estimatedGrade
      })
    },

    gradeClass(grade) {
      const map = {
        0: 'grade-0',
        1: 'grade-1',
        2: 'grade-2',
        3: 'grade-3',
        4: 'grade-4',
        5: 'grade-5',
        6: 'grade-6'
      }
      return map[grade] || 'grade-0'
    },

    validate() {
      const errors = {}
      if (!this.form.telId) {
        errors.telId = '请输入手机号'
      } else if (!/^1[3-9]\d{9}$/.test(this.form.telId)) {
        errors.telId = '手机号格式不正确'
      }
      if (!this.form.provinceId) errors.provinceId = '请选择省份'
      if (!this.form.cityId) errors.cityId = '请选择城市'
      if (!this.form.address) errors.address = '请输入详细地址'
      if (!this.form.afDate) errors.afDate = '请选择反馈日期'
      if (!this.form.afTime) errors.afTime = '请选择反馈时间'
      if (!this.form.information) errors.information = '请输入反馈信息'
      this.errors = errors
      return Object.keys(errors).length === 0
    },

    resetForm() {
      const afId = this.form.afId
      this.form = {
        afId: afId,
        telId: afId ? '' : (this.$store.getters.user ? this.$store.getters.user.telId : ''),
        provinceId: this.provinces[0] ? this.provinces[0].provinceId : null,
        cityId: null,
        address: '',
        afDate: '',
        afTime: '',
        information: '',
        estimatedGrade: 1
      }
      this.errors = {}
      if (this.form.provinceId) this.fetchCities(this.form.provinceId)
    },

    goBack() {
      this.$router.push('/aqiFeedback')
    },

    async handleSubmit() {
      if (!this.validate()) {
        this.showToast('warning', '请完善表单信息')
        return
      }
      this.submitting = true
      try {
        let res
        if (this.form.afId) {
          res = await updateAqiFeedback(this.form)
          if (res.data.code === 200) {
            this.showToast('success', '修改成功')
          } else {
            this.showToast('error', res.data.message || '修改失败')
            this.submitting = false
            return
          }
        } else {
          res = await saveAqiFeedback(this.form)
          if (res.data.code === 200) {
            this.showToast('success', '新增成功')
          } else {
            this.showToast('error', res.data.message || '新增失败')
            this.submitting = false
            return
          }
        }
        setTimeout(() => {
          this.$router.push('/aqiFeedback')
        }, 800)
      } catch (err) {
        console.log(err)
        this.showToast('error', '网络异常，请稍后重试')
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.form-page {
  min-height: calc(100vh - 68px);
  background: linear-gradient(180deg, var(--neps-bg-tint) 0, var(--neps-bg) 320px);
  padding: 40px 20px;
  box-sizing: border-box;
  text-align: left;
}

.form-card {
  max-width: 860px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid var(--neps-border);
  border-radius: 17px;
  box-shadow: 0 15px 45px rgba(31, 58, 80, 0.08);
  overflow: hidden;
}

.form-header {
  padding: 24px 32px;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fbfdfd;
}

.form-title {
  margin: 0;
  font-size: 20px;
  color: var(--neps-text);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: 1px solid #dcdfe6;
  color: #606266;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.back-btn:hover {
  color: var(--neps-primary);
  border-color: var(--neps-primary);
  background: #e8f7f2;
}

.back-arrow {
  font-size: 16px;
}

.aqi-form {
  padding: 32px;
}

.smart-fill{margin-bottom:24px;padding:18px;border:1px solid #cfe8df;border-radius:13px;background:linear-gradient(135deg,#effaf6,#f3f8fc)}
.smart-copy{display:flex;flex-direction:column;gap:4px;color:#1d6f5d}.smart-copy span{color:#657d8c;font-size:12px}
.smart-control{display:grid;grid-template-columns:1fr auto;gap:10px;margin-top:12px}.smart-control textarea{resize:vertical;padding:11px;border:1px solid #cddfe0;border-radius:9px;font:inherit;outline:0}.smart-control button{min-width:110px;border:0;border-radius:9px;background:linear-gradient(135deg,#17836a,#2573a5);color:#fff;font-weight:700;cursor:pointer}.smart-control button:disabled{opacity:.6}.smart-fill small{display:block;margin-top:9px;color:#53766d}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
}

.form-item.full-width {
  margin-bottom: 20px;
}

.form-label {
  font-size: 14px;
  color: #333;
  margin-bottom: 8px;
  font-weight: 500;
}

.required {
  color: #f56c6c;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  font-size: 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #fff;
  box-sizing: border-box;
  color: #333;
}

.form-input:focus {
  border-color: var(--neps-primary);
  box-shadow: 0 0 0 3px rgba(23, 131, 106, 0.13);
}

.form-input.has-error {
  border-color: #f56c6c;
}

.form-textarea {
  resize: vertical;
  font-family: inherit;
}

.form-input:disabled {
  background: #f5f7fa;
  color: #c0c4cc;
  cursor: not-allowed;
}

.form-input[readonly] {
  background: #f5f7fa;
  color: #606266;
  cursor: not-allowed;
}

.error-msg {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 6px;
  line-height: 1.2;
}

.grade-select.grade-1 { color: #1aad19; font-weight: 600; }
.grade-select.grade-2 { color: #a3c644; font-weight: 600; }
.grade-select.grade-3 { color: #f0a020; font-weight: 600; }
.grade-select.grade-4 { color: #f56c6c; font-weight: 600; }
.grade-select.grade-5 { color: #9c27b0; font-weight: 600; }
.grade-select.grade-6 { color: #7f0000; font-weight: 600; }

.form-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 12px;
  padding-top: 24px;
  border-top: 1px solid #eee;
}

.btn {
  padding: 10px 32px;
  font-size: 15px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, var(--neps-primary), var(--neps-secondary));
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(25, 119, 100, 0.24);
}

.btn-default {
  background: #fff;
  color: #606266;
  border: 1px solid #dcdfe6;
}

.btn-default:hover {
  color: var(--neps-primary);
  border-color: var(--neps-primary);
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  gap: 16px;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #e4e7ed;
  border-top-color: var(--neps-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-text {
  color: #909399;
  font-size: 14px;
}

.toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 22px;
  border-radius: 8px;
  font-size: 14px;
  z-index: 9999;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
  max-width: 90%;
  white-space: nowrap;
}

.toast.success { background: #f0f9eb; color: #67c23a; border: 1px solid #e1f3d8; }
.toast.error   { background: #fef0f0; color: #f56c6c; border: 1px solid #fde2e2; }
.toast.warning { background: #fdf6ec; color: #e6a23c; border: 1px solid #faecd8; }
.toast.info    { background: #edf2fc; color: #909399; border: 1px solid #ebeef5; }

.toast-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: currentColor;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
}

.toast-enter-active, .toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translate(-50%, -20px);
}

.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -20px);
}

@media (max-width: 640px) {
  .smart-control{grid-template-columns:1fr}.smart-control button{min-height:42px}
  .form-row {
    grid-template-columns: 1fr;
  }
  .form-card {
    border-radius: 0;
  }
  .form-page {
    padding: 0;
  }
}
</style>
