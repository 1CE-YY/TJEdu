package com.tianji.learning.service.impl;

// 略

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("ALL")
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper learningRecordMapper;

    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // 1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        // 2.循环遍历，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 2.1.获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            // 2.2.填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        // 3.批量新增
        saveBatch(list);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {

        // 1.获取当前登陆人
        Long userId = UserContext.getUser();
        if (userId == null) {
            // 用户未登录，无法查询
            log.error("用户未登录，无法查询课程表");
            throw new BadRequestException("用户未登录，无法查询课程表");
        }

        // 2.分页查询课程表
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (records == null || records.size() == 0) {
            // 没有查询到数据，直接返回空分页结果
            return PageDTO.empty(page);
        }
        if (CollUtils.isEmpty(records)) {
            // 课程表没有数据，直接返回空分页结果
            return PageDTO.empty(page);
        }
        // 3.远程调用课程服务给vo中课程名，封面，章节数复制
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(simpleInfoList)) {
            // 课程信息不存在，无法填充课程名等信息
            log.error("课程信息不存在，无法填充课程名等信息");
            throw new BizIllegalException("课程信息不存在，无法填充课程名等信息");
        }
        // 3.1.将课程信息转换为Map，方便后续查找
        Map<Long, CourseSimpleInfoDTO> infoMap = simpleInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        // 4.将po中的数据封装到vo中
        List<LearningLessonVO> vos = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);

            CourseSimpleInfoDTO courseSimpleInfoDTO = infoMap.get(record.getCourseId());

            if (courseSimpleInfoDTO != null) {
                vo.setCourseName(courseSimpleInfoDTO.getName());
                vo.setCourseCoverUrl(courseSimpleInfoDTO.getCoverUrl());
                vo.setSections(courseSimpleInfoDTO.getSectionNum());
            }
            vos.add(vo);
        }

        // 5.返回分页结果
        return PageDTO.of(page, vos);

    }

    @Override
    public LearningLessonVO queryMyCurrentLession() {
        // 1.获取当前登陆用户id
        Long userId = UserContext.getUser();

        // 2.查询当前用户最近学习的课程，降序排序，取第一条 正在学习中的 status = 1
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING) // 正在学习中
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();

        if (lesson == null) {
            // 没有正在学习的课程，直接返回null
            return null;
        }

        // 3.远程调用课程服务获取课程信息
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null) {
            // 课程信息不存在，无法填充课程名等信息
            log.error("课程信息不存在，无法填充课程名等信息");
            throw new BizIllegalException("课程信息不存在，无法填充课程名等信息");
        }

        // 4.查询总课程数
        Integer courseAmount = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();

        // 5.远程调用课程服务获取小节名称和小节编号
        Long latestSectionId = lesson.getLatestSectionId();//最近学习的小节id
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            // 小节信息不存在，无法填充小节名称和编号
            log.error("小节信息不存在，无法填充小节名称和编号");
            throw new BizIllegalException("小节信息不存在，无法填充小节名称和编号");
        }

        // 6.将课程信息封装到vo中
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(cinfo.getName());
        vo.setCourseCoverUrl(cinfo.getCoverUrl());
        vo.setSections(cinfo.getSectionNum());
        vo.setCourseAmount(courseAmount);//总课程数
        CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
        vo.setLatestSectionName(cataSimpleInfoDTO.getName());//学习的章节名称
        vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());//学习的章节编号

        // 7.返回vo
        return vo;
    }

    @Override
    public Long isLessonValid(Long courseId) {

        // 1.获取当前登陆用户id
        Long userId = UserContext.getUser();

        // 2.查询课表learning_lesson 条件user_id course_id
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            // 没有报名课程，返回null
            return null;
        }

        // 3.校验课程是否过期
        LocalDateTime expireTime = lesson.getExpireTime();
        if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
            // 课程已过期，返回null
            return null;
        }

        // 4.课程有效，返回lessonId
        return lesson.getId();

    }

    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {

        // 1.获取当前登陆用户id
        Long userId = UserContext.getUser();

        // 2.查询课表learning_lesson 条件user_id course_id
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            // 没有报名课程，返回null
            return null;
        }

        // 3.po转换为vo
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);

        return vo;
    }

    @Override
    public void createLearningPlan(LearningPlanDTO dto) {

        // 1.获取当前登陆用户id
        Long userId = UserContext.getUser();

        // 2.查询课表learning_lesson 条件user_id course_id
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();
        if (lesson == null) {
            // 没有报名课程，返回null
            throw new BizIllegalException("没有报名课程，无法创建学习计划");
        }

        // 3.修改课表
        this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq, dto.getFreq())
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING) // 设置状态为计划中
                .eq(LearningLesson::getUserId, userId)
                .update();

    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {

        // 1.获取当前登陆用户id
        Long userId = UserContext.getUser();

        // TODO 2.查询积分

        // 3.查询本周学习计划总数 learning_lesson 条件 userId status in (0,1)  plan_status = 1, sum(week_freq)
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");
        wrapper.eq("user_id", userId);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING); // 计划中或正在学习
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING); // 计划中
        Map<String, Object> map = this.getMap(wrapper);
        //{plansTotal : 7}
        Integer plansTotal = 0;
        if (map != null && map.get("plansTotal") != null) {
            // 如果查询结果不为空，获取计划总数
            plansTotal = Integer.parseInt(map.get("plansTotal").toString());
        }


        // 4.查询实际已学习总数 learing_record 条件 userId finish_time 在本周区间内 finished为true count(*)


        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now); // 本周一
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now); // 本周日
        Integer weekFinishedPlanNum = learningRecordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));


        // 5.查询课表数据 learning_lesson 条件 userId status in (0,1) plan_status = 1 分页
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING) // 计划中或正在学习
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING) // 计划中
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            // 没有查询到数据
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtils.emptyList());
            return vo;
        }

        // 6.远程调用课程服务获取课程信息
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)) {
            // 课程信息不存在，无法填充课程名等信息
            log.error("课程信息不存在，无法填充课程名等信息");
            throw new BizIllegalException("课程信息不存在，无法填充课程名等信息");
        }
        // 将cinfos list结构转为map <courseId, CourseSimpleInfoDTO>
        Map<Long, CourseSimpleInfoDTO> cinfoMap = cinfos.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        // 7.查询学习记录表 本周 当前用户下每一门课已学习的小节数量
        QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.select("lesson_id as lessonId, count(*) as userId");
        recordWrapper.eq("user_id", userId);
        recordWrapper.eq("finished", true); // 已完成的记录
        recordWrapper.between("finish_time", weekBeginTime, weekEndTime); // 本周内
        recordWrapper.groupBy("lesson_id");
        List<LearningRecord> learningRecords = learningRecordMapper.selectList(recordWrapper);
        // 将learningRecords list结构转为map <lessonId, Integer>，记录每门课已学习的小节数量
        // 注意：这里的userId实际上是每门课已学习的小节数量
        Map<Long, Long> courseWeekFinishNumMap = learningRecords.stream()
                .collect(Collectors.toMap(LearningRecord::getLessonId, c -> c.getUserId()));

        // 8.封装LearningPlanPageVO返回
        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekTotalPlan(plansTotal);
        vo.setWeekFinished(weekFinishedPlanNum);

        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson record : records) {
            LearningPlanVO planVO = BeanUtils.copyBean(record, LearningPlanVO.class);
            CourseSimpleInfoDTO infoDTO = cinfoMap.get(record.getCourseId());
            if (infoDTO != null) {
                planVO.setCourseName(infoDTO.getName());
                planVO.setSections(infoDTO.getSectionNum());
            }

            /*Long aLong = courseWeekFinishNumMap.get(record.getId());
            if (aLong != null) {
                // 如果本周已学习的小节数量不为null，则设置到vo中
                planVO.setWeekLearnedSections(aLong.intValue());
            } else {
                // 否则设置为0
                planVO.setWeekLearnedSections(0);
            }*/
            // 使用getOrDefault方法简化代码
            planVO.setWeekLearnedSections(courseWeekFinishNumMap.getOrDefault(record.getId(), 0L).intValue());


            voList.add(planVO);
        }

        /*vo.setList(voList);
        vo.setTotal(page.getTotal());
        vo.setPages(page.getPages());*/
        return vo.pageInfo(page.getTotal(), page.getPages(), voList);
    }


}