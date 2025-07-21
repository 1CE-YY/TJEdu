package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-11
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addPointsRecord(SignInMessage msg, PointsRecordType type) {
        // 0.校验参数
        if (msg.getUserId() == null || msg.getPoints() == null) {
            return;
        }

        int realPoints = msg.getPoints();// 代表实际可增加的积分
        // 1.判断该积分类型是否有上限 type.maxPoints
        int maxPoints = type.getMaxPoints();
        if (maxPoints > 0){
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = DateUtils.beginOfDay(now);
            LocalDateTime endOfDay = DateUtils.endOfDay(now);
            // 2.如果有上限，判断当前积分是否超过上限 条件 userId type 今天   sum(points)
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("sum(points) as totalPoints");
            wrapper.eq("user_id", msg.getUserId())
                    .eq("type", type);
            wrapper.between("create_time", startOfDay, endOfDay);

            Map<String, Object> map = this.getMap(wrapper);
            int currentPoints = 0;
            if (map != null) {
                BigDecimal totalPoints = (BigDecimal) map.get("totalPoints");//bigdecimal
                currentPoints = totalPoints != null ? totalPoints.intValue() : 0;
            }
            if (currentPoints >= maxPoints) {
                // 3.超过上限，直接返回
                return;
            }
            if (currentPoints + realPoints > maxPoints) {
                // 4.如果加上当前积分超过上限，计算实际可增加的积分
                realPoints = maxPoints - currentPoints;
            }
        }

        // 5.保存积分
        PointsRecord record = new PointsRecord();
        record.setUserId(msg.getUserId());
        record.setType(type);
        record.setPoints(realPoints);
        this.save(record);

        // 6.累加并保存总积分值到redis zset结构 当前赛季排行榜
        LocalDate today = LocalDate.now();
        String format = today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        redisTemplate.opsForZSet().incrementScore(key, msg.getUserId().toString(), realPoints);

    }

    @Override
    public List<PointsStatisticsVO> queryMyTodayPoints() {

        // 1.获取用户id
        Long userId = UserContext.getUser();

        // 2.查询积分表points_record 条件 userId 今日 按type分组 type,sum()
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = DateUtils.beginOfDay(now);
        LocalDateTime endOfDay = DateUtils.endOfDay(now);

        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.select("type", "sum(points) as points"); //借用points字段暂存结果
        wrapper.eq("user_id", userId);
        wrapper.between("create_time", startOfDay, endOfDay);
        wrapper.groupBy( "type");

        List<PointsRecord> records = this.list(wrapper);
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptyList();
        }

        // 3.查询结果转换为VO返回
        List<PointsStatisticsVO> voList = new ArrayList<>();
        for (PointsRecord record : records) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(record.getType().getDesc());//积分类型中文
            vo.setMaxPoints(record.getType().getMaxPoints()); //积分类型上限
            vo.setPoints(record.getPoints());
            voList.add(vo);
        }

        return voList;

    }
}
