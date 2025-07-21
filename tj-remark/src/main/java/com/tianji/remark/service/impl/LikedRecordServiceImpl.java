//package com.tianji.remark.service.impl;
//
//import com.tianji.api.dto.msg.LikedTimesDTO;
//import com.tianji.api.dto.trade.OrderBasicDTO;
//import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
//import com.tianji.common.constants.MqConstants;
//import com.tianji.common.utils.CollUtils;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.remark.domain.dto.LikeRecordFormDTO;
//import com.tianji.remark.domain.po.LikedRecord;
//import com.tianji.remark.mapper.LikedRecordMapper;
//import com.tianji.remark.service.ILikedRecordService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * <p>
// * 点赞记录表 服务实现类
// * </p>
// *
// * @author 1CE-YY
// * @since 2025-07-10
// */
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//    private final RabbitMqHelper rabbitMqHelper;
//
//    @Override
//    public void addLikeRecord(LikeRecordFormDTO dto) {
//
//        // 1.获取当前登录用户
//        Long userId = UserContext.getUser();
//
//        // 2.判断是否点赞
//        /*boolean flag = true;
//        if (dto.getLiked()) {
//            // 2.1点赞
//            flag = liked(dto);
//        } else {
//            // 2.2取消点赞
//            flag = unLiked(dto);
//        }*/
//        // 三位运算符
//        boolean flag = dto.getLiked() ? liked(dto, userId) : unLiked(dto, userId);
//        if (!flag) {
//            // 失败
//            return;
//        }
//
//        // 3.统计该业务下总点赞数
//        Integer totalLikesNum = this.lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .count();
//
//        // 4.发送消息到mq
//        log.debug("发送点赞消息到MQ，bizId: {}, totalLikesNum: {}", dto.getBizId(), totalLikesNum);
//        String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//        LikedTimesDTO msg = new LikedTimesDTO();
//        msg.setBizId(dto.getBizId());
//        msg.setLikedTimes(totalLikesNum);
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                routingKey,
//                msg);
//
//    }
//
//    @Override
//    public Set<Long> getLikesStatusByBizIds(Set<Long> bizIds) {
//
//        if (CollUtils.isEmpty(bizIds)) {
//            // 如果业务id集合为空，直接返回空集合
//            return CollUtils.emptySet();
//        }
//
//        // 1.获取当前登录用户
//        Long userId = UserContext.getUser();
//
//        // 2.查询点赞记录表
//        List<LikedRecord> recordList = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .in(LikedRecord::getBizId, bizIds)
//                .list();
//
//        // 3.转集合返回
//        Set<Long> likedBizIds = recordList.stream()
//                .map(LikedRecord::getBizId)
//                .collect(Collectors.toSet());
//
//
//        return likedBizIds;
//    }
//
//
//    //取消点赞
//    private boolean unLiked(LikeRecordFormDTO dto, Long userId) {
//        LikedRecord record = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if (record == null) {
//            // 没有点赞记录，无法取消点赞
//            return false;
//        }
//
//        return this.removeById(record.getId());
//    }
//
//    //点赞
//    private boolean liked(LikeRecordFormDTO dto, Long userId) {
//        LikedRecord record = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if (record != null) {
//            // 已经点赞了
//            return false;
//        }
//        LikedRecord likeRecord = new LikedRecord();
//        likeRecord.setUserId(userId);
//        likeRecord.setBizId(dto.getBizId());
//        likeRecord.setBizType(dto.getBizType());
//        return this.save(likeRecord);
//    }
//}
