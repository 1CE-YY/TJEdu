package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-10
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    void addLikeRecord(LikeRecordFormDTO likeRecordFormDTO);

    Set<Long> getLikesStatusByBizIds(Set<Long> bizIds);

    void readLikedTimesAndSendMessage(String bizType, int maxBizSize);
}
