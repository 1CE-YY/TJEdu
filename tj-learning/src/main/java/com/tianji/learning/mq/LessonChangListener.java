package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangListener {

    private final ILearningLessonService lessonService;

    @RabbitListener(bindings = @QueueBinding(
            value = @org.springframework.amqp.rabbit.annotation.Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @org.springframework.amqp.rabbit.annotation.Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, durable = "true", type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO orderBasicDTO) {

        // 1.健壮性校验
        if (orderBasicDTO == null || orderBasicDTO.getUserId() == null || orderBasicDTO.getCourseIds() == null || orderBasicDTO.getCourseIds().isEmpty()) {
            log.error("LessonChangListener.listenLessonPay: 监听到的订单支付消息不完整，无法为用户开通课程，orderBasicDTO={}", orderBasicDTO);
            return;
        }

        // 2.调用学习服务的相关方法，完成用户课程的开通，添加课程
        log.debug("LessonChangListener.listenLessonPay: 监听到订单支付成功消息，开始为用户开通课程，orderBasicDTO={}", orderBasicDTO);
        lessonService.addUserLessons(orderBasicDTO.getUserId(), orderBasicDTO.getCourseIds());

    }
}
