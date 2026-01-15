package com.tianji.learning.task;


import com.tianji.common.utils.CollUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;
import static com.tianji.learning.constants.RedisConstants.POINTS_BOARD_KEY_PREFIX;

@Component
@RequiredArgsConstructor
@Slf4j
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate redisTemplate;

    //@Scheduled(cron = "0 0 3 1 * ?") // 每月1号，凌晨3点执行
    @XxlJob("createTableJob") //xxl-job 定时任务
    public void createPointsBoardTableOfLastSeason(){
        // 1.获取上月时间
        LocalDate time = LocalDate.now().minusMonths(1);
        // 2.查询赛季id
        PointsBoardSeason season = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (season == null) {
            // 赛季不存在
            return;
        }
        // 3.创建表
        seasonService.createPointsBoardLatestTable(season.getId());
    }

    // 持久化上个月排行榜数据到db
    @XxlJob("savePointsBoard2DB") //任务名字要和xxl-job控制台 任务的jobhandler保持一致
    public void savePointsBoard2DB() {
        // 1.获取上月时间
        LocalDate time = LocalDate.now().minusMonths(1);

        // 2.查询赛季信息
        PointsBoardSeason season = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (season == null) {
            // 赛季不存在
            return;
        }

        // 3.计算动态表名 存入threadlocal
        String tableName = POINTS_BOARD_TABLE_PREFIX + season.getId();
        log.debug("持久化上赛季排行榜数据，动态表名为：{}", tableName);
        TableInfoContext.setInfo(tableName);

        // 4.判断上赛季排行数据是否存在 分页查询
        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = POINTS_BOARD_KEY_PREFIX + format;//boards:yyyyMM



        int shardIndex = XxlJobHelper.getShardIndex(); //当前任务的分片索引 从0开始
        int shardTotal = XxlJobHelper.getShardTotal(); //总分片数


        int pageNo = shardIndex + 1;
        int pageSize = 1000; //每页1000条数据
        while (true) {
            log.info("当前分片索引：{}，总分片数：{}，查询页码：{}", shardIndex, shardTotal, pageNo);
            List<PointsBoard> pointsBoardList = pointsBoardService.queryCurrentBoard(key, pageNo, pageSize);
            if (CollUtils.isEmpty(pointsBoardList)) {
                // 没有数据了，退出循环
                log.info("上赛季排行榜数据持久化完成，动态表名为：{}", tableName);
                break;
            }
            pageNo += shardTotal;

            // 5.持久化到db相应的表单中
            for (PointsBoard board : pointsBoardList) {
                board.setId(Long.valueOf(board.getRank())); // 将排名作为主键id
                board.setRank(null);
            }
            pointsBoardService.saveBatch(pointsBoardList);

            // 删除本页数据
        }

        // 6.删除动态表名的threadlocal
        TableInfoContext.remove();

    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key

        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = POINTS_BOARD_KEY_PREFIX + format;//boards:yyyyMM
        // 3.删除
        redisTemplate.unlink(key);
    }
}
