package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-08
 */
@Api(tags = "互动问题相关接口")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("新增提问")
    @PostMapping
    public void saveQuestion(@Validated @RequestBody QuestionFormDTO questionDTO){
        questionService.saveQuestion(questionDTO);
    }

    @ApiOperation("修改互动问题")
    @PutMapping("{id}")
    public void updateQuestion(@PathVariable Long id , @RequestBody QuestionFormDTO questionDTO) {
        questionService.updateQuestion(id, questionDTO);
    }

    @ApiOperation("分页查询互动问题列表-用户端")
    @GetMapping("page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        return questionService.queryQuestionPage(query);
    }

    @ApiOperation("查询互动问题详情-用户端")
    @GetMapping("{id}")
    public QuestionVO queryQuestionById(@PathVariable Long id) {
        return questionService.queryQuestionById(id);
    }

}
