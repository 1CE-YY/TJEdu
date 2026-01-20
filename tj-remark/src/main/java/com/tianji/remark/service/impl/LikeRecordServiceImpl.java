package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;


@Slf4j
@Service
@RequiredArgsConstructor
public class LikeRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;


    @Override
    public void addLikeRecord(LikeRecordFormDTO likeRecordFormDTO) {
        boolean success = likeRecordFormDTO.getLiked() ? like(likeRecordFormDTO) : unlike(likeRecordFormDTO);
        if (!success) {
            return;
        }
        Integer likeTimes = lambdaQuery()
                .eq(LikedRecord::getBizId, likeRecordFormDTO.getBizId())
                .count();
        // 发送消息更新点赞数
        rabbitMqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, likeRecordFormDTO.getBizType()),
                new LikedTimesDTO(likeRecordFormDTO.getBizId(), likeTimes)
        );

    }

    private boolean unlike(LikeRecordFormDTO likeRecordFormDTO) {
        return remove(new QueryWrapper<LikedRecord>().lambda()
                .eq(LikedRecord::getBizId, likeRecordFormDTO.getBizId())
                .eq(LikedRecord::getUserId, UserContext.getUser()));
    }

    private boolean like(LikeRecordFormDTO likeRecordFormDTO) {
        Long userId = UserContext.getUser();

        Integer count = lambdaQuery()
                .eq(LikedRecord::getBizId, likeRecordFormDTO.getBizId())
                .eq(LikedRecord::getUserId, userId)
                .count();
        if (count > 0) {
            return false;
        }
        LikedRecord likedRecord = BeanUtils.copyBean(likeRecordFormDTO, LikedRecord.class);
        likedRecord.setUserId(userId);
        return save(likedRecord);

    }

    @Override
    public Set<Long> getLikesStatusByBizIds(Set<Long> bizIds) {

        Long userId = UserContext.getUser();

        List<LikedRecord> list = lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();

        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {

    }
}
