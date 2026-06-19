<template>
  <div class="list-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">短链接列表</span>
          <div class="header-actions">
            <el-button @click="fetchData" :loading="loading" class="btn-refresh">
              <el-icon><Refresh /></el-icon>
            </el-button>
            <el-button type="danger" plain :disabled="!selectedRows.length" @click="handleBatchDelete">
              <el-icon><Delete /></el-icon>
              批量删除 {{ selectedRows.length ? `(${selectedRows.length})` : '' }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <div class="search-bar">
        <el-input
          v-model="queryParams.keyword"
          placeholder="搜索链接或描述..."
          clearable
          style="width: 280px"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>

        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="~"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="handleDateChange"
        />

        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        @selection-change="handleSelectionChange"
        style="width: 100%"
        stripe
        :header-cell-style="{ backgroundColor: '#fafbfc', fontWeight: 600, color: '#374151', fontSize: '13px' }"
      >
        <el-table-column type="selection" width="44" />

        <el-table-column label="短码" width="140">
          <template #default="{ row }">
            <div class="code-cell">
              <code class="short-code">{{ row.shortCode }}</code>
              <el-button link size="small" class="btn-icon" @click="copyUrl(row.shortUrl)">
                <el-icon :size="14"><DocumentCopy /></el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="原始链接" min-width="240">
          <template #default="{ row }">
            <div class="link-cell">
              <el-text class="original-url" truncated>{{ row.originalUrl }}</el-text>
              <el-button link size="small" class="btn-icon" @click="copyUrl(row.originalUrl)">
                <el-icon :size="14"><DocumentCopy /></el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="描述" min-width="140">
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="点击量" prop="clickCount" width="90" sortable>
          <template #default="{ row }">
            <span class="click-count">{{ row.clickCount || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <div class="status-cell">
              <el-switch
                v-model="row.status"
                :active-value="1"
                :inactive-value="0"
                @change="handleStatusChange(row)"
                size="small"
              />
              <span class="status-label" :class="row.status === 1 ? 'on' : 'off'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="有效期" width="160">
          <template #default="{ row }">
            <template v-if="row.expireTime">
              <span class="time-text" :class="{ expired: isExpired(row.expireTime) }">
                {{ formatTime(row.expireTime) }}
              </span>
              <el-tag v-if="isExpired(row.expireTime)" type="danger" size="small" class="tag-expired">过期</el-tag>
            </template>
            <span v-else class="time-text muted">永久有效</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="action-cell">
              <el-button link type="primary" size="small" @click="showQRCode(row)">
                二维码
              </el-button>
              <el-button link type="primary" size="small" @click="handleViewStats(row)">
                统计
              </el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无短链接数据" :image-size="80" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
          small
          background
        />
      </div>
    </el-card>

    <!-- 二维码弹窗 -->
    <el-dialog v-model="qrcodeVisible" title="短链接二维码" width="300px" align-center>
      <div class="qrcode-dialog">
        <canvas ref="qrcodeCanvasRef"></canvas>
        <code class="qrcode-url">{{ qrcodeUrl }}</code>
        <el-button type="primary" size="small" @click="copyUrl(qrcodeUrl)">
          <el-icon><DocumentCopy /></el-icon>
          复制链接
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, DocumentCopy } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { getShortLinkList, updateShortLinkStatus, deleteShortLink, batchDeleteShortLinks } from '../api/shortlink'
import { copyToClipboard } from '../utils/clipboard'

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const selectedRows = ref([])
const dateRange = ref([])

const qrcodeVisible = ref(false)
const qrcodeUrl = ref('')
const qrcodeCanvasRef = ref(null)

const queryParams = reactive({
  keyword: '',
  status: null,
  startTime: '',
  endTime: '',
  pageNum: 1,
  pageSize: 10,
  sortField: 'createdTime',
  sortOrder: 'desc'
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getShortLinkList(queryParams)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('获取列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  fetchData()
}

const handleReset = () => {
  queryParams.keyword = ''
  queryParams.status = null
  queryParams.startTime = ''
  queryParams.endTime = ''
  dateRange.value = []
  queryParams.pageNum = 1
  fetchData()
}

const handleDateChange = (val) => {
  if (val) {
    queryParams.startTime = val[0]
    queryParams.endTime = val[1]
  } else {
    queryParams.startTime = ''
    queryParams.endTime = ''
  }
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const handleStatusChange = async (row) => {
  try {
    await updateShortLinkStatus(row.shortCode, row.status)
    ElMessage.success(`已${row.status === 1 ? '启用' : '禁用'}`)
  } catch (error) {
    row.status = row.status === 1 ? 0 : 1
  }
}

const handleViewStats = (row) => {
  router.push(`/stats/${row.shortCode}`)
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该短链接吗？删除后无法恢复。', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      confirmButtonClass: 'el-button--danger'
    })
    await deleteShortLink(row.shortCode)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

const handleBatchDelete = async () => {
  if (!selectedRows.value.length) return

  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条短链接吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '全部删除',
      confirmButtonClass: 'el-button--danger'
    })
    const shortCodes = selectedRows.value.map(row => row.shortCode)
    await batchDeleteShortLinks(shortCodes)
    ElMessage.success('批量删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
    }
  }
}

const copyUrl = async (url) => {
  try {
    await copyToClipboard(url)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

const showQRCode = (row) => {
  qrcodeUrl.value = row.shortUrl
  qrcodeVisible.value = true
  nextTick(() => {
    if (qrcodeCanvasRef.value) {
      QRCode.toCanvas(qrcodeCanvasRef.value, row.shortUrl, {
        width: 180,
        margin: 2,
        color: { dark: '#1d1d1f', light: '#ffffff' }
      })
    }
  })
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const isExpired = (expireTime) => {
  if (!expireTime) return false
  return new Date(expireTime) < new Date()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.btn-refresh {
  padding: 8px;
}

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  align-items: center;
}

/* Table cells */
.code-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.short-code {
  font-family: 'SF Mono', SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-primary);
  background-color: #f0f5ff;
  padding: 2px 8px;
  border-radius: 4px;
}

.link-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn-icon {
  opacity: 0.4;
  transition: opacity 0.15s ease;
}

.btn-icon:hover {
  opacity: 1;
}

.original-url {
  max-width: 220px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.desc-text {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.click-count {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-label {
  font-size: 12px;
}

.status-label.on {
  color: var(--color-success);
}

.status-label.off {
  color: var(--color-text-muted);
}

.time-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.time-text.expired {
  color: var(--color-danger);
}

.time-text.muted {
  color: var(--color-text-muted);
}

.tag-expired {
  margin-left: 4px;
}

.action-cell {
  display: flex;
  gap: 4px;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

/* QR dialog */
.qrcode-dialog {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.qrcode-dialog canvas {
  border-radius: 8px;
}

.qrcode-url {
  font-size: 12px;
  color: var(--color-text-muted);
  word-break: break-all;
  text-align: center;
  max-width: 240px;
  font-family: 'SF Mono', SFMono-Regular, Consolas, monospace;
}
</style>
