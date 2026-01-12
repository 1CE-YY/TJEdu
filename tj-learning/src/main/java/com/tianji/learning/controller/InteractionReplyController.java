package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import com.tianji.learning.service.impl.InteractionReplyServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-08
 */
@Api(tags = "互动问题的回答或评论相关接口")
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;

    @ApiOperation("添加互动问题的回答或评论")
    @PostMapping
    public void saveReply(@RequestBody @Validated ReplyDTO replyDTO) {
        replyService.saveReply(replyDTO);
    }

    @ApiOperation("客户端分页查询回答或者评论列表")
    @GetMapping("page")
    public PageDTO<ReplyVO> queryReplyVoPage(ReplyPageQuery query) {
        return replyService.queryReplyVoPage(query);
    }
}
