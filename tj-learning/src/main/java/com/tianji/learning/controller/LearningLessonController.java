package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-05
 */
@Api(tags = "我的课程相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/lessons")
public class LearningLessonController {

    final ILearningLessonService lessonService;

    @ApiOperation("分页查询我的课表")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        return lessonService.queryMyLessons(query);
    }

    @ApiOperation("查询正在学习的课表")
    @GetMapping("now")
    public LearningLessonVO queryMyCurrentLession() {
        return lessonService.queryMyCurrentLession();
    }

    /**
     * 校验当前用户是否可以学习当前课程
     * @param courseId 课程id
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    @ApiOperation("校验当前用户是否可以学习当前课程")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId) {
        return lessonService.isLessonValid(courseId);
    }

    @ApiOperation("查询用户课表中指定课程状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO queryLessonByCourseId(@PathVariable("courseId") Long courseId) {
        return lessonService.queryLessonByCourseId(courseId);
    }

    /**
     * 创建学习计划
     */
    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlan(@RequestBody @Validated LearningPlanDTO dto) {
        lessonService.createLearningPlan(dto);
    }

    /**
     * 查询当前用户的学习计划
     */
    @ApiOperation("分页查询当前用户的学习计划")
    @GetMapping("plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        return lessonService.queryMyPlans(query);
    }

}
