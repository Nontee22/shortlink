import request from './request'

/**
 * 获取短链接统计信息
 * @param {String} shortCode - 短码
 */
export function getStats(shortCode) {
  return request({
    url: `/stats/${shortCode}`,
    method: 'get'
  })
}

/**
 * 获取PV
 * @param {String} shortCode - 短码
 */
export function getPv(shortCode) {
  return request({
    url: `/stats/${shortCode}/pv`,
    method: 'get'
  })
}

/**
 * 获取UV
 * @param {String} shortCode - 短码
 */
export function getUv(shortCode) {
  return request({
    url: `/stats/${shortCode}/uv`,
    method: 'get'
  })
}
