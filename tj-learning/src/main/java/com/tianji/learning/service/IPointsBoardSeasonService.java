package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-11
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    void createPointsBoardLatestTable(Integer id);
}
