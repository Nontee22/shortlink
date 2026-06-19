<template>
  <div class="stats-container" v-loading="loading">
    <!-- 返回 -->
    <div class="back-row">
      <el-button link @click="router.back()" class="btn-back">
        <el-icon><ArrowLeft /></el-icon>
        返回列表
      </el-button>
    </div>

    <!-- 基础信息 -->
    <el-card class="info-card">
      <div class="info-row">
        <div class="info-main">
          <div class="info-code">
            <code>{{ stats.shortCode }}</code>
          </div>
          <el-link type="primary" :href="stats.originalUrl" target="_blank" class="info-url">
            {{ stats.originalUrl }}
          </el-link>
        </div>
        <div class="info-time">
          <span class="info-label">创建于</span>
          <span>{{ formatTime(stats.createdTime) }}</span>
        </div>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-card-inner">
          <div class="stat-header">
            <el-icon :size="18" class="stat-icon pv"><View /></el-icon>
            <span class="stat-label">总访问量 (PV)</span>
          </div>
          <div class="stat-value">{{ stats.pv || 0 }}</div>
        </div>
        <div class="stat-bar pv"></div>
      </div>

      <div class="stat-card">
        <div class="stat-card-inner">
          <div class="stat-header">
            <el-icon :size="18" class="stat-icon uv"><User /></el-icon>
            <span class="stat-label">独立访客 (UV)</span>
          </div>
          <div class="stat-value">{{ stats.uv || 0 }}</div>
        </div>
        <div class="stat-bar uv"></div>
      </div>

      <div class="stat-card">
        <div class="stat-card-inner">
          <div class="stat-header">
            <el-icon :size="18" class="stat-icon today-pv"><TrendCharts /></el-icon>
            <span class="stat-label">今日访问</span>
          </div>
          <div class="stat-value">{{ stats.todayPv || 0 }}</div>
        </div>
        <div class="stat-bar today-pv"></div>
      </div>

      <div class="stat-card">
        <div class="stat-card-inner">
          <div class="stat-header">
            <el-icon :size="18" class="stat-icon today-uv"><UserFilled /></el-icon>
            <span class="stat-label">今日访客</span>
          </div>
          <div class="stat-value">{{ stats.todayUv || 0 }}</div>
        </div>
        <div class="stat-bar today-uv"></div>
      </div>
    </div>

    <!-- 图表 -->
    <div class="charts-row">
      <el-card class="chart-card chart-trend">
        <template #header>
          <span class="section-title">访问趋势（近 7 天）</span>
        </template>
        <div ref="trendChartRef" class="chart"></div>
      </el-card>

      <el-card class="chart-card chart-device">
        <template #header>
          <span class="section-title">设备分布</span>
        </template>
        <div ref="deviceChartRef" class="chart"></div>
      </el-card>
    </div>

    <!-- 最近访问 -->
    <el-card class="access-card">
      <template #header>
        <span class="section-title">最近访问</span>
      </template>
      <el-table
        :data="stats.recentAccess"
        max-height="360"
        stripe
        :header-cell-style="{ backgroundColor: '#fafbfc', fontWeight: 600, color: '#374151', fontSize: '13px' }"
      >
        <el-table-column prop="ip" label="IP 地址" min-width="130">
          <template #default="{ row }">
            <code class="ip-text">{{ row.ip }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="deviceType" label="设备" min-width="80">
          <template #default="{ row }">
            <span class="device-tag" :class="(row.deviceType || '').toLowerCase()">
              {{ row.deviceType || '未知' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="browser" label="浏览器" min-width="130" show-overflow-tooltip />
        <el-table-column prop="os" label="系统" min-width="110" show-overflow-tooltip />
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.accessTime) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无访问记录" :image-size="60" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ArrowLeft, View, User, TrendCharts, UserFilled } from '@element-plus/icons-vue'
import { getStats } from '../api/stats'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const stats = ref({})

const trendChartRef = ref(null)
const deviceChartRef = ref(null)

let trendChart = null
let deviceChart = null

const fetchStats = async () => {
  const shortCode = route.params.shortCode
  if (!shortCode) return

  loading.value = true
  try {
    const data = await getStats(shortCode)
    stats.value = data
    nextTick(() => {
      initCharts()
    })
  } catch (error) {
    console.error('获取统计失败:', error)
  } finally {
    loading.value = false
  }
}

const initCharts = () => {
  initTrendChart()
  initDeviceChart()
}

const initTrendChart = () => {
  if (!trendChartRef.value) return

  trendChart = echarts.init(trendChartRef.value)
  const trendData = stats.value.dailyTrend || []

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#374151', fontSize: 13 },
      axisPointer: { type: 'cross', crossStyle: { color: '#e5e7eb' } }
    },
    legend: {
      data: ['PV', 'UV'],
      top: 0,
      right: 0,
      textStyle: { color: '#6b7280', fontSize: 12 }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '36px',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.map(item => item.date),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    series: [
      {
        name: 'PV',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trendData.map(item => item.pv),
        lineStyle: { width: 2, color: '#2563eb' },
        itemStyle: { color: '#2563eb' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(37, 99, 235, 0.12)' },
            { offset: 1, color: 'rgba(37, 99, 235, 0.01)' }
          ])
        }
      },
      {
        name: 'UV',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trendData.map(item => item.uv),
        lineStyle: { width: 2, color: '#16a34a' },
        itemStyle: { color: '#16a34a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(22, 163, 74, 0.12)' },
            { offset: 1, color: 'rgba(22, 163, 74, 0.01)' }
          ])
        }
      }
    ]
  }

  trendChart.setOption(option)
}

const initDeviceChart = () => {
  if (!deviceChartRef.value) return

  deviceChart = echarts.init(deviceChartRef.value)
  const deviceData = stats.value.deviceStats || []

  const option = {
    tooltip: {
      trigger: 'item',
      backgroundColor: '#fff',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#374151', fontSize: 13 }
    },
    color: ['#2563eb', '#6b7280', '#d1d5db'],
    series: [
      {
        type: 'pie',
        radius: ['45%', '72%'],
        center: ['50%', '55%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 3
        },
        label: {
          show: true,
          formatter: '{b}\n{c}',
          fontSize: 12,
          color: '#6b7280',
          lineHeight: 18
        },
        data: deviceData.map(item => ({
          name: item.device,
          value: item.count
        }))
      }
    ]
  }

  deviceChart.setOption(option)
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const handleResize = () => {
  trendChart?.resize()
  deviceChart?.resize()
}

onMounted(() => {
  fetchStats()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  deviceChart?.dispose()
})
</script>

<style scoped>
.stats-container {
  max-width: 1100px;
  margin: 0 auto;
}

.back-row {
  margin-bottom: 20px;
}

.btn-back {
  color: var(--color-text-secondary);
  font-size: 14px;
}

.btn-back:hover {
  color: var(--color-text);
}

/* Info card */
.info-card {
  margin-bottom: 24px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
}

.info-main {
  min-width: 0;
  flex: 1;
}

.info-code code {
  font-family: 'SF Mono', SFMono-Regular, Consolas, monospace;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.info-url {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-time {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.info-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
}

/* Stat grid */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  overflow: hidden;
  transition: box-shadow 0.2s ease;
  box-shadow: var(--shadow-card);
}

.stat-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.stat-card-inner {
  padding: 20px 20px 16px;
}

.stat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.stat-icon {
  opacity: 0.8;
}

.stat-icon.pv { color: #2563eb; }
.stat-icon.uv { color: #16a34a; }
.stat-icon.today-pv { color: #d97706; }
.stat-icon.today-uv { color: #dc2626; }

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.stat-bar {
  height: 3px;
}

.stat-bar.pv { background-color: #2563eb; }
.stat-bar.uv { background-color: #16a34a; }
.stat-bar.today-pv { background-color: #d97706; }
.stat-bar.today-uv { background-color: #dc2626; }

/* Charts */
.charts-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.chart-card {
  min-height: 360px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.chart {
  height: 280px;
}

/* Access table */
.access-card {
  margin-bottom: 24px;
}

.ip-text {
  font-family: 'SF Mono', SFMono-Regular, Consolas, monospace;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.device-tag {
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
  display: inline-block;
}

.device-tag.mobile {
  background-color: #f0fdf4;
  color: #16a34a;
}

.device-tag.pc {
  background-color: #eff6ff;
  color: #2563eb;
}

.device-tag.tablet {
  background-color: #fef3c7;
  color: #d97706;
}

.time-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .charts-row {
    grid-template-columns: 1fr;
  }

  .info-row {
    flex-direction: column;
  }

  .info-time {
    align-items: flex-start;
  }
}
</style>
