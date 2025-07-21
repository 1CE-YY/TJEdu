package com.tianji.learning.controller;


import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-11
 */
@Api(tags = "积分榜-赛季相关接口")
@RestController
@RequestMapping("/boards/seasons")
@RequiredArgsConstructor
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService pointsBoardSeasonService;


    @ApiOperation("查询赛季列表")
    @RequestMapping("list")
    public List<PointsBoardSeason> queryPotinBoardSeasonList() {
        return pointsBoardSeasonService.list();
    }
}
