package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-07
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler taskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {

        // 1.获取当前用户的登录id
        Long userId = UserContext.getUser();

        // 2.查询课表信息 条件user_id 和 course_id
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            log.warn("用户[{}]查询课程[{}]的学习记录时，未找到对应的课表信息", userId, courseId);
            throw new BizIllegalException("未找到对应的课表信息，请检查课程是否存在或已被删除");
        }

        // 3.查询学习记录 条件user_id 和 lesson_id
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();

        // 4.封装返回结果
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        List<LearningRecordDTO> dtoList = BeanUtils.copyList(recordList, LearningRecordDTO.class);
        dto.setRecords(dtoList);

        return dto;
    }

    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO dto) {

        // 1.获取当前用户的登录id
        Long userId = UserContext.getUser();

        // 2.处理学习记录
        boolean isFinish = false;//代表本小节是否学完
        if (dto.getSectionType().equals(SectionType.EXAM)) {
            // 2.1视频记录
            isFinish = handleVidioRecord(userId, dto);
        } else if (dto.getSectionType().equals(SectionType.VIDEO)) {
            // 2.2考试记录
            isFinish = handleExamRecord(userId, dto);
        } else {
            throw new BizIllegalException("小节类型错误，只能是：1-视频，2-考试");
        }

        if (!isFinish) {
            // 如果本小节没有学完，则直接返回
            log.info("用户[{}]提交学习记录成功，但本小节未学完，lessonId: {}, sectionId: {}", userId, dto.getLessonId(), dto.getSectionId());
            return;
        }

        // 3.处理课表数据
        handleLessonData(dto);
    }

    private void handleLessonData(LearningRecordFormDTO dto) {

        // 1.查询课表信息 learning_lesson 条件 lesson_id主键
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if (lesson == null) {
            log.error("未找到对应的课表信息，lessonId: {}", dto.getLessonId());
            throw new BizIllegalException("未找到对应的课表信息，请检查课程是否存在或已被删除");
        }

        // 2.判断是否第一次学完 isFinish是不是true
        boolean allSectionsFinished = false; // 是否全部小节都学完了
        // 3.远程调用课程服务 得到课程信息 小节总数
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null) {
            log.error("未找到对应的课程信息，courseId: {}", lesson.getCourseId());
            throw new BizIllegalException("未找到对应的课程信息，请检查课程是否存在或已被删除");
        }
        Integer sectionNum = cinfo.getSectionNum();// 获取课程的小节总数

        // 4.如果isFinish为true 本小节是第一次学完 判断该用户对课程下全部小节是否都学完了
        Integer learnedSections = lesson.getLearnedSections();// 获取用户已学小节数
        allSectionsFinished = learnedSections + 1 >= sectionNum; // +1是因为本小节刚学完

        // 5.如果全部小节都学完了 则更新课表信息 learning_lesson 的最新小节id和状态
        lessonService.lambdaUpdate()
                .set(lesson.getStatus() == LessonStatus.NOT_BEGIN, LearningLesson::getStatus, LessonStatus.LEARNING)
                .set(LearningLesson::getLatestSectionId, dto.getSectionId()) // 更新最新小节id
                .set(allSectionsFinished, LearningLesson::getStatus, LessonStatus.FINISHED) // 更新是否学完状态
                .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
                .setSql("learned_sections = learned_sections + 1") // 更新已学小节数
                .eq(LearningLesson::getId, lesson.getId())
                .update();

    }

    private boolean handleVidioRecord(Long userId, LearningRecordFormDTO dto) {

        // 1.查询旧的学习记录 learning_record 条件 userId 和 lessonId 和 sectionId

        LearningRecord learningRecord = queryOldRecord(dto.getLessonId(), dto.getSectionId());
//        LearningRecord learningRecord = this.lambdaQuery()
//                .eq(LearningRecord::getLessonId, dto.getLessonId())
//                .eq(LearningRecord::getSectionId, dto.getSectionId())
//                .one();

        // 2.判断是否存在旧的学习记录
        if (learningRecord == null) {
            // 3.如果不存在旧的学习记录，则创建新的学习记录
            // 将dto转换为po对象
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);

            // 保留学习记录
            boolean isSuccess = this.save(record);
            if (!isSuccess) {
                log.error("用户[{}]提交学习记录失败，记录信息：{}", userId, record);
                throw new DbException("提交考试记录失败，请稍后重试");
            }
            return false; // 返回false表示本小节没有学完
        }
        // 4.如果存在旧的学习记录，则更新旧的学习记录
        // 判断本小节是否第一次学完 为true是第一次学完
        boolean isFirstFinish = !learningRecord.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        if (!isFirstFinish) {
            LearningRecord record = new LearningRecord();
            record.setId(learningRecord.getId());
            record.setLessonId(learningRecord.getLessonId());
            record.setSectionId(learningRecord.getSectionId());
            record.setMoment(learningRecord.getMoment() + dto.getMoment()); // 累加当前观看时长
            record.setFinished(learningRecord.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false; // 返回false表示本小节没有学完
        }

        boolean result = this.lambdaUpdate()
                .eq(LearningRecord::getId, learningRecord.getId())
                .set(LearningRecord::getMoment, dto.getMoment()) // 更新当前观看时长
                .set(isFirstFinish, LearningRecord::getFinished, true) // 更新是否学完状态
                .set(isFirstFinish, LearningRecord::getFinishTime, dto.getCommitTime()) // 更新提交时间
                .update();
        if (!result) {
            log.error("用户[{}]更新学习记录失败，记录信息：{}", userId, learningRecord);
            throw new DbException("更新学习记录失败，请稍后重试");
        }

        // 清理redis缓存
        taskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());


        return isFirstFinish;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1.查询缓存
        LearningRecord cache = taskHandler.readRecordCache(lessonId, sectionId);

        // 2.命中则返回
        if (cache != null) {
            log.info("命中缓存，lessonId: {}, sectionId: {}, record: {}", lessonId, sectionId, cache);
            return cache;
        }

        // 3.未命中则查询数据库
        LearningRecord dbRecord = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        if (dbRecord == null) {
            log.info("未找到旧的学习记录，lessonId: {}, sectionId: {}", lessonId, sectionId);
            return null; // 没有旧记录
        }

        // 4.放入缓存
        taskHandler.writeRecordCache(dbRecord);

        return dbRecord;
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {

        // 1.将dto转换为po对象
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true); // 提交考试记录，代表本小节已经学完
        record.setFinishTime(dto.getCommitTime() == null ? null : dto.getCommitTime()); // 提交时间

        // 2.保留学习记录
        boolean isSuccess = this.save(record);
        if (isSuccess) {
            log.info("用户[{}]提交考试记录成功，记录信息：{}", userId, record);
            return true;
        } else {
            log.error("用户[{}]提交考试记录失败，记录信息：{}", userId, record);
            throw new DbException("提交考试记录失败，请稍后重试");
        }

    }
}
