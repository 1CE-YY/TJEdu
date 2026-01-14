package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BitField;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper rabbitMqHelper;

    @Override
    public SignResultVO addSignRecords() {

        // 1.获取当前登录用户的id
        Long userId = UserContext.getUser();

        // 2.拼接key
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
//        sdf.format(new Date());
        LocalDate now = LocalDate.now(); // 获取当前日期年月
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;

        // 3.用bitset命令保存 需要校验是否已签到
        Boolean setBit = redisTemplate.opsForValue().setBit(key, now.getDayOfMonth() - 1, true);
        if (setBit) {
            throw new BizIllegalException("今天已经签到过了，请勿重复签到！");
        }

        // 4.计算连续签到天数
        int days = countSignDays(key, now.getDayOfMonth());

        // 5.计算联系签到的奖励积分
        int rewardPoints = 0;
        if (days == 7) {
            rewardPoints = 10; // 连续签到7天奖励10积分
        } else if (days == 14) {
            rewardPoints = 20; // 连续签到14天奖励20积分
        } else if (days == 28) {
            rewardPoints = 40; // 连续签到28天奖励30积分
        }

        // 6.保存积分 发送消息到mq
        rabbitMqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));


        // 7.封装vo返回
        SignResultVO resultVO = new SignResultVO();
        resultVO.setSignDays(days); // 设置连续签到天数
        resultVO.setRewardPoints(rewardPoints); // 设置奖励积分

        return null;
    }


    /**
     * 计算连续签到的天数
     *
     * @param key        redis中的key
     * @param dayOfMonth 当前日期是几号
     * @return 连续签到的天数
     */
    private int countSignDays(String key, int dayOfMonth) {

        // 计算连续签到的天数
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField)) {
            return 0; // 没有签到记录
        }
        Long num = bitField.get(0);//第一天
        log.debug("签到记录的二进制数值为: {}", num);

        int counter = 0;
        while ((num & 1) == 1) {
            counter++;

            num = num >>> 1; // 右移一位，检查下一个签到记录
        }

        return counter;
    }

    @Override
    public Byte[] querySignRecords() {

        // 1.获取当前登录用户的id
        Long userId = UserContext.getUser();

        // 2.拼接key
        LocalDate now = LocalDate.now(); // 获取当前日期年月
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;

        // 3.用bitfield获取签到记录
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth())).valueAt(0));
        if (CollUtils.isEmpty(bitField))  {
            return new Byte[0]; // 没有签到记录
        }
        int offset = now.getDayOfMonth() - 1; // 获取当前日期是几号
        // 4.转换为byte数组返回
        Byte[] result = new Byte[now.getDayOfMonth()];
        Long num = bitField.get(0); // 获取签到记录的二进制数值
        while (offset >= 0) {
            result[offset] = (byte) (num & 1); // 取最低位，判断是否签到
            num = num >>> 1; // 右移一位，检查下一个签到记录
            offset--;
        }

        return result;
    }

}
