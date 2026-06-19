import request from './request'

/**
 * 创建短链接
 * @param {Object} data - { originalUrl, description, expireDays }
 */
export function createShortLink(data) {
  return request({
    url: '/short-link/create',
    method: 'post',
    data
  })
}

/**
 * 获取短链接列表
 * @param {Object} params - 查询参数
 */
export function getShortLinkList(params) {
  return request({
    url: '/short-link/list',
    method: 'get',
    params
  })
}

/**
 * 获取短链接详情
 * @param {String} shortCode - 短码
 */
export function getShortLinkDetail(shortCode) {
  return request({
    url: `/short-link/${shortCode}`,
    method: 'get'
  })
}

/**
 * 更新短链接状态
 * @param {String} shortCode - 短码
 * @param {Number} status - 状态: 0-禁用, 1-启用
 */
export function updateShortLinkStatus(shortCode, status) {
  return request({
    url: `/short-link/${shortCode}/status`,
    method: 'put',
    params: { status }
  })
}

/**
 * 删除短链接
 * @param {String} shortCode - 短码
 */
export function deleteShortLink(shortCode) {
  return request({
    url: `/short-link/${shortCode}`,
    method: 'delete'
  })
}

/**
 * 批量删除短链接
 * @param {Array} shortCodes - 短码列表
 */
export function batchDeleteShortLinks(shortCodes) {
  return request({
    url: '/short-link/batch',
    method: 'delete',
    data: shortCodes
  })
}
