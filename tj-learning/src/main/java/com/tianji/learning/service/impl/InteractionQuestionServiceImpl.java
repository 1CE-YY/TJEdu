package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-08
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;

    private final UserClient userClient;

    private final SearchClient searchClient;

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        // 1.获取当前登录用户的ID
        Long userId = UserContext.getUser();

        // 2.将DTO转换为PO对象
        InteractionQuestion question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        question.setUserId(userId); // 设置用户ID

        // 3.保存
        this.save(question);

    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {

        // 1.校验
        if (StringUtils.isBlank(dto.getTitle()) || StringUtils.isBlank(dto.getDescription()) || dto.getAnonymity() == null) {
            throw new BadRequestException("非法参数，标题、描述和匿名状态不能为空");
        }
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("非法参数，ID不存在");
        }
        // 校验当前用户是否是提问者
        if (!question.getUserId().equals(UserContext.getUser())) {
            throw new BadRequestException("非法操作，您不是该问题的提问者");
        }

        // 2.将DTO转换为PO对象
        question.setTitle(dto.getTitle());
        question.setDescription(dto.getDescription());
        question.setAnonymity(dto.getAnonymity());

        // 3.更新
        this.updateById(question);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {

        // 1.校验查询条件 courseId
        if (query.getCourseId() == null) {
            throw new BadRequestException("非法参数，课程ID不能为空");
        }

        // 2.获取当前登录用户的ID
        Long userId = UserContext.getUser();

        // 3.查询分页数据interaction_question 条件 courseId onlyMine为true才回家userId 小节id不为空 hidden为false
        Page<InteractionQuestion> page = this.lambdaQuery()
                //.select(InteractionQuestion::getId, InteractionQuestion::getTitle, InteractionQuestion::getCourseId)
                .select(InteractionQuestion.class, tableFieldInfo -> !tableFieldInfo.getProperty().equals("description"))
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .eq(query.getOnlyMine() != null && query.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }


        Set<Long> latestAnswerIds = new HashSet<>();// 最新回答ID集合
        Set<Long> userIds = new HashSet<>();// 互动问题的用户ID集合
        for (InteractionQuestion record : records) {
            if (!record.getAnonymity()) {
                // 3.1 收集用户ID
                userIds.add(record.getUserId());
            }

            // 3.2 收集最新回答ID
            if (record.getLatestAnswerId() != null) {
                latestAnswerIds.add(record.getLatestAnswerId());
            }
        }
//        // stream流实现
//        Set<Long> latestAnswerIds = records.stream()
//                .filter(record -> record.getLatestAnswerId() != null)
//                .map(InteractionQuestion::getLatestAnswerId)
//                .collect(Collectors.toSet());


        // 4.根据最新回答id 获取回答信息 条件
        Map<Long, InteractionReply> replyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(latestAnswerIds)) {
//            List<InteractionReply> replyList = replyService.listByIds(latestAnswerIds);
            List<InteractionReply> replyList = replyService.list(Wrappers.<InteractionReply>lambdaQuery().in(InteractionReply::getId, latestAnswerIds)
                    .eq(InteractionReply::getHidden, false));
            for (InteractionReply reply : replyList) {
                // 4.1 收集最新回答的用户ID
                if (!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
                replyMap.put(reply.getId(), reply);
            }
//            replyMap = replyList.stream()
//                    .collect(Collectors.toMap(InteractionReply::getId, reply -> reply, (oldValue, newValue) -> oldValue));
        }


        // 5.远程调用用户服务 获取用户信息 批量
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        // 5.1 将用户信息转换为Map
        Map<Long, UserDTO> userMap = userDTOS.stream()
                .collect(Collectors.toMap(UserDTO::getId, c -> c));

        // 6.转换为VO对象返回
        List<QuestionVO> vos = BeanUtils.copyList(records, QuestionVO.class);
        for (InteractionQuestion record : records) {
            QuestionVO questionVO = BeanUtils.copyBean(record, QuestionVO.class);
            if (!questionVO.getAnonymity()) {
                UserDTO userDTO = userMap.get(record.getUserId());
                if (userDTO != null) {
                    questionVO.setUserName(userDTO.getName());
                    questionVO.setUserIcon(userDTO.getIcon());
                }

            }


            InteractionReply reply = replyMap.get(record.getLatestAnswerId());
            if (reply != null) {
                if (!reply.getAnonymity()) {
                    UserDTO replyUser = userMap.get(reply.getUserId());
                    if (replyUser != null) {
                        questionVO.setLatestReplyUser(replyUser.getName());//最新回答者的昵称
                    }

                }
                questionVO.setLatestReplyContent(reply.getContent());//最新回答内容
            }

            vos.add(questionVO);
        }

        return PageDTO.of(page, vos);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {

        // 1.校验ID
        if (id == null || id <= 0) {
            throw new BadRequestException("非法参数，ID不能为空或小于等于0");
        }


        // 2.查询互动问题 按主键查询
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("非法参数，问题不存在");
        }

        // 3.管理员设置隐藏 返回空
        if (question.getHidden()) {
            return null;
        }
        // 4.封装vo返回
        QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);

        // 5.如果用户匿名提问 不用查询提问者昵称和头像
        if (!question.getAnonymity()) {
            // 4.1 获取提问者的用户信息
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO != null) {
                questionVO.setUserName(userDTO.getName());
                questionVO.setUserIcon(userDTO.getIcon());
            }
        }

        return questionVO;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query) {

        // 0.如果用户传了课程名称参数，则要从es中获取该名称对应的课程id
        List<Long> courseIds = null;
        if (StringUtils.isNotBlank(query.getCourseName())) {
            // 0.1 通过feign远程调用搜索服务获取课程ID
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if (CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }

        // 1.查询互动问题表 分页 排序按提问时间倒序
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(courseIds), InteractionQuestion::getCourseId, courseIds)
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .between(query.getBeginTime() != null && query.getEndTime() != null,
                        InteractionQuestion::getCreateTime, query.getBeginTime(), query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        Set<Long> uids = new HashSet<>(); // 用于存储提问者的用户ID
        Set<Long> cids = new HashSet<>(); // 用于存储课程ID
        Set<Long> chapterAndSectionIds = new HashSet<>(); // 用于存储章节和节的ID
        for (InteractionQuestion record : records) {
            uids.add(record.getUserId());
            cids.add(record.getCourseId());
            chapterAndSectionIds.add(record.getChapterId());
            chapterAndSectionIds.add(record.getSectionId());
        }
        // 2.远程调用用户服务获取用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BizIllegalException("无法获取提问者的用户信息, 用户不存在");
        }
        // 2.1 将用户信息转换为Map
        Map<Long, UserDTO> userMap = userDTOS.stream()
                .collect(Collectors.toMap(UserDTO::getId, c -> c));

        // 3.远程调用课程服务获取课程信息
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(cids);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BizIllegalException("无法获取课程信息, 课程不存在");
        }
        // 3.1 将课程信息转换为Map
        Map<Long, CourseSimpleInfoDTO> courseMap = cinfos.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        // 4.远程调用章节服务获取章节信息
        List<CataSimpleInfoDTO> cataInfos = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isEmpty(cataInfos)) {
            throw new BizIllegalException("无法获取章节信息, 章节不存在");
        }
        // 4.1 将章节信息转换为Map
        Map<Long, String> cataMap = cataInfos.stream()
                .collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));


        // 6.转换为VO对象返回
        List<QuestionAdminVO> vos = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionAdminVO vo = BeanUtils.copyBean(record, QuestionAdminVO.class);
            // 6.0 获取提问者的用户信息
            UserDTO userDTO = userMap.get(record.getUserId());
            if (userDTO != null) {
                // 6.1 如果用户没有匿名提问，则设置提问者的昵称
                vo.setUserName(userDTO.getName());
            }
            // 6.2 获取课程名称
            CourseSimpleInfoDTO courseInfo = courseMap.get(record.getCourseId());
            if (courseInfo != null) {
                vo.setCourseName(courseInfo.getName());
                List<Long> categoryIds = courseInfo.getCategoryIds();// 获取课程的分类ID列表
                // 5.远程调用分类服务获取分类信息
                String categoryNames = categoryCache.getCategoryNames(categoryIds);
                vo.setCategoryName(categoryNames != null ? categoryNames : "未分类");
            }

            // 6.3 获取章节名称
            String chapterName = cataMap.get(record.getChapterId());
            vo.setChapterName(chapterName != null ? chapterName : "");

            // 6.4 获取节名称
            String sectionName = cataMap.get(record.getSectionId());
            vo.setSectionName(sectionName != null ? sectionName : "");

            vos.add(vo);
        }
        return PageDTO.of(page, vos);
    }
}
