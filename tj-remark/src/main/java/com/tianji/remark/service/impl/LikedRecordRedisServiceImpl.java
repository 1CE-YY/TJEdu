package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {

        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.判断是否点赞
        /*boolean flag = true;
        if (dto.getLiked()) {
            // 2.1点赞
            flag = liked(dto);
        } else {
            // 2.2取消点赞
            flag = unLiked(dto);
        }*/
        // 三位运算符
        boolean flag = dto.getLiked() ? liked(dto, userId) : unLiked(dto, userId);
        if (!flag) {
            // 失败
            return;
        }

        // 3.统计该业务下总点赞数
//        Integer totalLikesNum = this.lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .count();
        // 基于Redis统计点赞数
        // 拼接key likes:set:biz:评论id
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long totalLikesNum = redisTemplate.opsForSet().size(key);
        if (totalLikesNum == null || totalLikesNum < 0) {
            // 如果统计结果为null或小于0，说明统计失败
            log.error("统计点赞数失败，bizId: {}", dto.getBizId());
            return;
        }

        // 4.缓存点赞总数 zset结构
        // 拼接key likes:times:type:业务类型
        String bizTypeTotalLikeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet().add(bizTypeTotalLikeKey, dto.getBizId().toString(), totalLikesNum);




//        log.debug("发送点赞消息到MQ，bizId: {}, totalLikesNum: {}", dto.getBizId(), totalLikesNum);
//        String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//        LikedTimesDTO msg = new LikedTimesDTO();
//        msg.setBizId(dto.getBizId());
//        msg.setLikedTimes(totalLikesNum);
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                routingKey,
//                msg);


    }

    @Override
    public Set<Long> getLikesStatusByBizIds(Set<Long> bizIds) {

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

//        // 基于Redis查询点赞状态
//        // 1.获取用户
//        Long userId = UserContext.getUser();
//
//        // 2.循环bizIds，查询每个业务id的点赞状态
//        Set<Long> likedBizIds = new HashSet<>();
//        if (CollUtils.isEmpty(bizIds)) {
//            // 如果业务id集合为空，直接返回空集合
//            return CollUtils.emptySet();
//        }
//        for (Long bizId : bizIds) {
//            // 拼接key likes:set:biz:评论id
//            String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
//
//            // redisTemplate 查询用户是否点赞
//            Boolean isLiked = redisTemplate.opsForSet().isMember(key, userId.toString());
//            if (isLiked != null && isLiked) {
//                // 如果用户点赞了，则添加到结果集中
//                likedBizIds.add(bizId);
//            }
//        }

        // 处理空集合情况
        if (bizIds == null || bizIds.isEmpty()) {
            return Collections.emptySet();
        }

        // 获取当前登录用户ID
        Long userId = UserContext.getUser();

        // 将Set转换为List以确保顺序一致
        List<Long> bizIdList = new ArrayList<>(bizIds);

        // 使用Redis管道批量查询点赞状态
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIdList) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });

        // 处理查询结果
        Set<Long> likedBizIds = new HashSet<>();
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) != null && (Boolean) results.get(i)) {
                likedBizIds.add(bizIdList.get(i));
            }
        }

        return likedBizIds;

    }




    //取消点赞
    private boolean unLiked(LikeRecordFormDTO dto, Long userId) {
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
        // 基于Redis的取消点赞逻辑
        // 拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();

        // redisTemplate 从redis中移除点赞记录
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        return result != null && result > 0; // 如果返回值大于0，说明移除成功
    }

    //点赞
    private boolean liked(LikeRecordFormDTO dto, Long userId) {
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

        // 基于Redis的点赞逻辑
        // 拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();

        // redisTemplate 往redis中set结构添加点赞记录
        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        return result != null && result > 0; // 如果返回值大于0，说明添加成功
    }




    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 1.拼接key likes:times:type:业务类型 QA NOTE
        String bizTypeTotalLikeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + bizType;

        // 2.从redis的zset中获取点赞数前maxBizSize个业务id，从小到大获取
        List<LikedTimesDTO> list = new ArrayList<>();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                .popMin(bizTypeTotalLikeKey, maxBizSize);
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String bizId = typedTuple.getValue();
            Double likedTimes = typedTuple.getScore();
            if (StringUtils.isBlank(bizId) || likedTimes == null) {
                // 如果bizId或likedTimes为空，跳过
                continue;
            }
            // 3.封装dto
            LikedTimesDTO dto = new LikedTimesDTO();
            dto.setBizId(Long.parseLong(bizId));
            dto.setLikedTimes(likedTimes.intValue());
            list.add(dto);
        }


        // 4.发送消息到MQ


        if (CollUtils.isNotEmpty(list)) {
            log.debug("发送点赞数变更消息到MQ，消息内容: {}", list);
            String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType);
            rabbitMqHelper.send(
                    MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                    routingKey,
                    list);
        }

    }
}
