<template>
  <div class="home-container">
    <el-card class="create-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">创建短链接</span>
          <span class="card-subtitle">将长链接转换为简短易分享的短链接</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        class="create-form"
      >
        <el-form-item label="原始链接" prop="originalUrl">
          <el-input
            v-model="form.originalUrl"
            placeholder="https://www.example.com/very-long-url..."
            clearable
            size="large"
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            placeholder="可选，简要描述该链接用途"
            clearable
          />
        </el-form-item>

        <el-form-item label="自定义短码" prop="customCode">
          <el-input
            v-model="form.customCode"
            placeholder="可选，如 my-promo"
            clearable
            maxlength="8"
            show-word-limit
          >
            <template #prepend>/sl/</template>
          </el-input>
          <div class="form-tip">2-8位字母、数字或连字符，留空则自动生成</div>
        </el-form-item>

        <el-form-item label="有效期" prop="expireDays">
          <el-select v-model="form.expireDays" placeholder="请选择有效期" style="width: 100%">
            <el-option label="永久有效" :value="0" />
            <el-option label="7 天" :value="7" />
            <el-option label="30 天" :value="30" />
            <el-option label="90 天" :value="90" />
            <el-option label="365 天" :value="365" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <div class="form-actions">
            <el-button type="primary" size="large" :loading="loading" @click="handleCreate" class="btn-create">
              生成短链接
            </el-button>
            <el-button size="large" @click="handleReset" class="btn-reset">
              重置
            </el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <transition name="result-fade">
      <el-card v-if="result" class="result-card">
        <div class="result-content">
          <div class="result-left">
            <div class="result-badge">
              <el-icon :size="16"><SuccessFilled /></el-icon>
              生成成功
            </div>

            <div class="result-field">
              <label>短链接</label>
              <div class="short-url-row">
                <code class="short-url">{{ result.shortUrl }}</code>
                <el-button type="primary" size="small" @click="copyUrl" class="btn-copy">
                  <el-icon><DocumentCopy /></el-icon>
                  复制
                </el-button>
              </div>
            </div>

            <div class="result-field">
              <label>原始链接</label>
              <el-text class="original-url" truncated>{{ result.originalUrl }}</el-text>
            </div>

            <div class="result-field">
              <label>创建时间</label>
              <span class="meta-text">{{ formatTime(result.createdTime) }}</span>
            </div>
          </div>

          <div class="result-right">
            <canvas ref="qrcodeCanvas" class="qrcode"></canvas>
            <span class="qr-label">扫码访问</span>
          </div>
        </div>
      </el-card>
    </transition>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { SuccessFilled, DocumentCopy } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { createShortLink } from '../api/shortlink'
import { copyToClipboard } from '../utils/clipboard'

const formRef = ref(null)
const qrcodeCanvas = ref(null)
const loading = ref(false)
const result = ref(null)

const form = ref({
  originalUrl: '',
  description: '',
  customCode: '',
  expireDays: 0
})

const customCodeValidator = (rule, value, callback) => {
  if (!value) return callback()
  if (value.length < 2) return callback(new Error('自定义短码至少2位'))
  if (value.length > 8) return callback(new Error('自定义短码最多8位'))
  if (!/^[A-Za-z0-9]([A-Za-z0-9-]{0,6}[A-Za-z0-9])?$/.test(value)) {
    return callback(new Error('仅支持字母、数字和连字符，不能以连字符开头或结尾'))
  }
  callback()
}

const rules = {
  originalUrl: [
    { required: true, message: '请输入原始链接', trigger: 'blur' },
    { type: 'url', message: 'URL格式不正确', trigger: 'blur' }
  ],
  customCode: [
    { validator: customCodeValidator, trigger: 'blur' }
  ]
}

const handleCreate = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await createShortLink({
      originalUrl: form.value.originalUrl,
      description: form.value.description || null,
      customCode: form.value.customCode || null,
      expireDays: form.value.expireDays || null
    })
    result.value = data
    ElMessage.success('短链接创建成功')

    nextTick(() => {
      if (qrcodeCanvas.value && data.shortUrl) {
        QRCode.toCanvas(qrcodeCanvas.value, data.shortUrl, {
          width: 120,
          margin: 2,
          color: { dark: '#1d1d1f', light: '#ffffff' }
        })
      }
    })
  } catch (error) {
    console.error('创建失败:', error)
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  formRef.value.resetFields()
  result.value = null
}

const copyUrl = async () => {
  if (result.value?.shortUrl) {
    try {
      await copyToClipboard(result.value.shortUrl)
      ElMessage.success('已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败，请手动复制')
    }
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped>
.home-container {
  max-width: 720px;
  margin: 0 auto;
}

.create-card {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
}

.card-subtitle {
  font-size: 13px;
  color: var(--color-text-muted);
}

.create-form {
  padding: 8px 0;
}

.form-actions {
  display: flex;
  gap: 12px;
}

.btn-create {
  min-width: 140px;
  font-weight: 500;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.btn-create:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
}

.btn-reset {
  color: var(--color-text-secondary);
}

.form-tip {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
  line-height: 1.4;
}

/* Result card */
.result-card {
  border-left: 3px solid var(--color-success);
}

.result-content {
  display: flex;
  gap: 32px;
  align-items: flex-start;
}

.result-left {
  flex: 1;
  min-width: 0;
}

.result-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.result-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background-color: #f0fdf4;
  color: var(--color-success);
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 20px;
}

.result-field {
  margin-bottom: 16px;
}

.result-field label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 6px;
}

.short-url-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.short-url {
  font-family: 'SF Mono', SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 15px;
  color: var(--color-primary);
  background-color: #f8f9fb;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  word-break: break-all;
}

.btn-copy {
  flex-shrink: 0;
}

.original-url {
  max-width: 100%;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.meta-text {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.qrcode {
  border-radius: 8px;
}

.qr-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

/* Result card entrance */
.result-fade-enter-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.result-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
</style>
