package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-08
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;

    private final UserClient userClient;

    @Override
    public void saveReply(ReplyDTO dto) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.保存回答或者评论
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        this.save(reply);

        // 3.判断是否是回答 answerId 是否为空
        // 获取问题实体
        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());
        if (dto.getAnswerId() != null) {
            // 3.1如果不是回答 累加回答下的评论数
            InteractionReply answerInfo = this.getById(dto.getAnswerId());
            answerInfo.setReplyTimes(answerInfo.getReplyTimes() + 1);
            this.updateById(answerInfo);

        } else {
            // 3.2如果是回答 则需要累加问题的回答数 修改最近一次回答id
            question.setLatestAnswerId(reply.getId());
            question.setAnswerTimes(question.getAnswerTimes() + 1);
        }

        // 4.判断是否是学生提交 true为学生提交 如果是将status改为未查看
        if (dto.getIsStudent()) {
            question.setStatus(QuestionStatus.UN_CHECK);
        }
        questionMapper.updateById(question);
    }

    @Override
    public PageDTO<ReplyVO> queryReplyVoPage(ReplyPageQuery query) {

        // 1.校验questionId和answerId是否为空
        if (query.getQuestionId() == null && query.getAnswerId() == null) {
            log.error("查询回答或评论列表时，questionId和answerId不能同时为空");
            throw new BadRequestException("查询回答或评论列表时，questionId和answerId不能同时为空");
        }

        // 2.分页查询interaction_reply表
        Page<InteractionReply> page = this.lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0 : query.getAnswerId())
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPage(
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)
                ));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        // 3.补全其他数据
        Set<Long> uids = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();
        for (InteractionReply record : records) {
            if (!record.getAnonymity()) {
                uids.add(record.getUserId());
                uids.add(record.getTargetUserId());
            }
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                targetReplyIds.add(record.getTargetReplyId());
            }
        }
        if (targetReplyIds.size() > 0) {
            List<InteractionReply> targetReplies = this.listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity)) // 过滤掉匿名回复
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            uids.addAll(targetUserIds);
        }

        List<UserDTO> userDTOList = userClient.queryUserByIds(uids);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (userDTOList != null) {
            userDTOMap = userDTOList.stream()
                    .collect(Collectors.toMap(UserDTO::getId, c -> c));
        }

        // 4.转换为VO
        List<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            if (!record.getAnonymity()) {
                // 如果不是匿名回复 则设置用户信息
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserType(userDTO.getType());
                }
            }
            UserDTO targetUserDTO = userDTOMap.get(record.getTargetUserId());
            if (targetUserDTO != null) {
                vo.setTargetUserName(targetUserDTO.getName());
            }
            voList.add(vo);
        }
        // 5.返回分页结果
        return PageDTO.of(page, voList);
    }
}
