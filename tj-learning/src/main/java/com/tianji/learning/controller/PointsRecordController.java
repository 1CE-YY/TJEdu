package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.IPointsRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 前端控制器
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-11
 */
@Api(tags = "学习积分相关接口")
@RestController
@RequestMapping("/points-record")
@RequiredArgsConstructor
public class PointsRecordController {

    private final IPointsRecordService pointsRecordService;

    @ApiOperation("查询今日积分情况")
    @GetMapping("today")
    public List<PointsStatisticsVO> queryMyTodayPoints() {
        return pointsRecordService.queryMyTodayPoints();
    }

}
