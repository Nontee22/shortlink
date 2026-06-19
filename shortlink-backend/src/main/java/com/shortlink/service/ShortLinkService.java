package com.shortlink.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.dto.ShortLinkCreateDTO;
import com.shortlink.dto.ShortLinkQueryDTO;
import com.shortlink.vo.ShortLinkVO;

import java.util.List;

/**
 * 短链接服务接口
 */
public interface ShortLinkService {

    /**
     * 创建短链接
     * @param dto 创建参数
     * @return 短链接信息
     */
    ShortLinkVO create(ShortLinkCreateDTO dto);

    /**
     * 根据短码获取原始URL（用于跳转）
     * @param shortCode 短码
     * @return 原始URL
     */
    String getOriginalUrl(String shortCode);

    /**
     * 分页查询短链接列表
     * @param queryDTO 查询参数
     * @return 分页结果
     */
    Page<ShortLinkVO> getPage(ShortLinkQueryDTO queryDTO);

    /**
     * 根据短码获取短链接详情
     * @param shortCode 短码
     * @return 短链接详情
     */
    ShortLinkVO getByShortCode(String shortCode);

    /**
     * 更新短链接状态
     * @param shortCode 短码
     * @param status 状态
     */
    void updateStatus(String shortCode, Integer status);

    /**
     * 删除短链接
     * @param shortCode 短码
     */
    void delete(String shortCode);

    /**
     * 批量删除短链接
     * @param shortCodes 短码列表
     */
    void batchDelete(List<String> shortCodes);
}
