package com.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 短链接Mapper
 */
@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {

    /**
     * 根据短码查询
     */
    ShortLink selectByShortCode(@Param("shortCode") String shortCode);

    /**
     * 根据原始URL查询
     */
    ShortLink selectByOriginalUrl(@Param("urlHash") String urlHash, @Param("originalUrl") String originalUrl);

    /**
     * 增加点击次数
     */
    int incrementClickCount(@Param("shortCode") String shortCode);

    int updateClickCount(@Param("shortCode") String shortCode, @Param("clickCount") long clickCount);

    /**
     * 物理删除：根据短码直接删除记录
     */
    int physicalDeleteByShortCode(@Param("shortCode") String shortCode);

    /**
     * 物理删除：批量根据短码直接删除记录
     */
    int physicalDeleteBatchByShortCodes(@Param("shortCodes") List<String> shortCodes);
}
