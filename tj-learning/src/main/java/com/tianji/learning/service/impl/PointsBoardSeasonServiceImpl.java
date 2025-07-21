package com.tianji.learning.service.impl;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-11
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    /**
     * 创建积分榜最新赛季表
     * @param id
     */
    @Override
    public void createPointsBoardLatestTable(Integer id) {
        getBaseMapper().createPointsBoardTable(POINTS_BOARD_TABLE_PREFIX + id);
    }
}
