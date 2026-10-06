<template>
  <div class="list-page">
    <transition name="toast">
      <div v-if="toast.show" class="toast" :class="toast.type">
        <span class="toast-icon">{{ toastIcon }}</span>
        <span class="toast-msg">{{ toast.message }}</span>
      </div>
    </transition>

    <section class="page-hero">
      <div><p>AIR QUALITY STANDARD</p><h1>空气质量指数级别{{ isAdmin ? '管理' : '标准' }}</h1><span>统一查看 AQI 六级标准、污染物浓度区间及对应健康建议。</span></div>
      <button v-if="isAdmin" class="btn btn-primary" @click="$router.push('/aqiForm')"><b>＋</b> 新增级别</button>
    </section>

    <div class="list-card">
      <div class="list-header">
        <div><h2 class="list-title">AQI 六级标准</h2><span>共 {{ list.length }} 个等级</span></div>
        <span class="unit-tip">SO₂ / SPM：μg/m³　·　CO：mg/m³</span>
      </div>

      <div v-if="loading" class="loading-wrap">
        <div class="spinner"></div>
        <span class="loading-text">数据加载中...</span>
      </div>

      <div v-else class="table-wrap">
        <table class="aqi-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>级别</th>
              <th>描述</th>
              <th>颜色</th>
              <th>SO₂ 范围</th>
              <th>CO 范围</th>
              <th>SPM 范围</th>
              <th>健康影响</th>
              <th>建议措施</th>
              <th v-if="isAdmin">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="item.aqiId">
              <td>{{ item.aqiId }}</td>
              <td>
                <span class="grade-tag" :style="{ background: item.color, color: getContrastColor(item.color) }">
                  {{ item.chineseExplain }}
                </span>
              </td>
              <td>{{ item.aqiExplain }}</td>
              <td>
                <span class="color-cell">
                  <span class="color-block" :style="{ background: item.color }"></span>
                  <span class="color-value">{{ item.color }}</span>
                </span>
              </td>
              <td>{{ item.so2Min }} ~ {{ item.so2Max }}</td>
              <td>{{ item.coMin }} ~ {{ item.coMax }}</td>
              <td>{{ item.spmMin }} ~ {{ item.spmMax }}</td>
              <td class="text-ellipsis" :title="item.healthImpact">{{ item.healthImpact }}</td>
              <td class="text-ellipsis" :title="item.takeSteps">{{ item.takeSteps }}</td>
              <td v-if="isAdmin" class="action-cell">
                <button class="link-btn edit" @click="$router.push('/aqiForm?aqiId=' + item.aqiId)">修改</button>
                <button class="link-btn delete" @click="handleDelete(item.aqiId)">删除</button>
              </td>
            </tr>
            <tr v-if="list.length === 0">
              <td colspan="10" class="empty-cell">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script>
import { getAqiList, deleteAqi } from '../api/aqi.js'

export default {
  name: 'AqiList',
  data() {
    return {
      list: [],
      loading: true,
      toast: {
        show: false,
        type: 'success',
        message: ''
      },
      toastTimer: null
    }
  },
  computed: {
    isAdmin() {
      return this.$store.getters.user?.role === 'ADMIN'
    },
    toastIcon() {
      const map = { success: '✓', error: '✕', warning: '!', info: 'i' }
      return map[this.toast.type] || 'i'
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    showToast(type, message) {
      this.toast = { show: true, type, message }
      if (this.toastTimer) clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => {
        this.toast.show = false
      }, 2500)
    },

    async fetchList() {
      this.loading = true
      try {
        const res = await getAqiList()
        if (res.data.code === 200) {
          this.list = res.data.data || []
        } else {
          this.showToast('error', res.data.message || '查询失败')
        }
      } catch (err) {
        this.showToast('error', '网络异常，请稍后重试')
      } finally {
        this.loading = false
      }
    },

    getContrastColor(hex) {
      if (!hex || hex.length < 7) return '#fff'
      const r = parseInt(hex.substr(1, 2), 16)
      const g = parseInt(hex.substr(3, 2), 16)
      const b = parseInt(hex.substr(5, 2), 16)
      const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
      return luminance > 0.6 ? '#333' : '#fff'
    },

    async handleDelete(aqiId) {
      if (!confirm('确定要删除该级别吗？删除后不可恢复。')) return
      try {
        const res = await deleteAqi(aqiId)
        if (res.data.code === 200) {
          this.showToast('success', '删除成功')
          this.fetchList()
        } else {
          this.showToast('error', res.data.message || '删除失败')
        }
      } catch (err) {
        this.showToast('error', '网络异常，请稍后重试')
      }
    }
  }
}
</script>

<style scoped>
.list-page {
  min-height: calc(100vh - 68px);
  background: linear-gradient(180deg, var(--neps-bg-tint) 0, var(--neps-bg) 320px);
  padding: 38px clamp(18px,4vw,58px) 70px;
  box-sizing: border-box;
  text-align: left;
}
.page-hero{max-width:1400px;margin:0 auto 25px;display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.page-hero p{margin:0;color:#078467;font-size:11px;font-weight:800;letter-spacing:1.6px}.page-hero h1{margin:8px 0 7px;color:#12354b;font-size:clamp(29px,3vw,39px);letter-spacing:-.7px}.page-hero span{color:#728595;font-size:13px}.page-hero .btn{height:44px;padding:0 19px;border-radius:10px}.page-hero .btn b{margin-right:4px;font-size:17px}

.list-card {
  max-width: 1400px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid var(--neps-border);
  border-radius: 17px;
  box-shadow: 0 15px 45px rgba(31, 58, 80, 0.08);
  overflow: hidden;
}

.list-header {
  padding: 24px 32px;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(90deg,#fbfdfd,#f7fbfc);
}
.list-header>div{display:flex;align-items:center;gap:10px}.list-header>div span,.unit-tip{color:#8797a3;font-size:12px}.unit-tip{padding:7px 10px;border-radius:8px;background:#eef6f5}

.list-title {
  margin: 0;
  font-size: 20px;
  color: var(--neps-text);
}

.btn {
  padding: 9px 22px;
  font-size: 14px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
}

.btn-primary {
  background: linear-gradient(135deg, var(--neps-primary), var(--neps-secondary));
  color: #fff;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(25, 119, 100, 0.24);
}

.table-wrap {
  padding: 0 25px 25px;
  overflow-x: auto;
}

.aqi-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  margin-top: 0;
}

.aqi-table thead th {
  background: #f4f8f8;
  color: #606266;
  font-weight: 600;
  padding: 15px 12px;
  text-align: left;
  border-bottom: 2px solid #ebeef5;
  white-space: nowrap;
  position: sticky;
  top: 0;
}

.aqi-table tbody td {
  padding: 14px 12px;
  border-bottom: 1px solid #ebeef5;
  color: #333;
  vertical-align: middle;
}

.aqi-table tbody tr:hover {
  background: #f4fbf8;
}

.grade-tag {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 13px;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.color-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.color-block {
  width: 22px;
  height: 22px;
  border-radius: 7px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  display: inline-block;
}

.color-value {
  font-family: monospace;
  font-size: 13px;
  color: #666;
}

.text-ellipsis {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
  vertical-align: middle;
}

.action-cell {
  white-space: nowrap;
}

.link-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 14px;
  padding: 4px 10px;
  border-radius: 4px;
  transition: all 0.2s;
}

.link-btn.edit {
  color: var(--neps-primary);
}

.link-btn.edit:hover {
  background: #e8f7f2;
}

.link-btn.delete {
  color: var(--neps-danger);
}

.link-btn.delete:hover {
  background: var(--neps-danger-soft);
}

.empty-cell {
  text-align: center !important;
  color: #909399;
  padding: 60px 0 !important;
  font-size: 15px;
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

@keyframes spin {
  to { transform: rotate(360deg); }
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
@media(max-width:700px){.page-hero{align-items:flex-start;flex-direction:column}.page-hero .btn{width:100%}.list-header{align-items:flex-start;gap:10px;flex-direction:column}.unit-tip{width:100%;box-sizing:border-box}.list-page{padding:25px 12px 50px}}
</style>
