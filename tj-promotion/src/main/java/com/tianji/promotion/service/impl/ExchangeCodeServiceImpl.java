package com.tianji.promotion.service.impl;

import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_RANGE_KEY;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;

    @Override
    @Async("generateExchangeCodeExecutor") //使用自定义线程池线程
    public void asyncGenerateExchangeCodes(Coupon coupon) {

        log.debug("开始异步生成兑换码，线程名：{}", Thread.currentThread().getName());

        Integer totalNum = coupon.getTotalNum();// 获取生成兑换码的数量

        // 调用incrby 批量生成兑换码 或循环
        // 1.生成自增id redis的incr方法
        Long increment = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if (increment == null) {
            return;
        }


        // 2.循环生成兑换码调用工具类生成兑换码
        int maxSerial = increment.intValue();//本次生成的最大序列号
        int begin = maxSerial - totalNum + 1; // 本次生成的最小序列号

        List<ExchangeCode> list = new ArrayList<>();
        for (int serialNum = begin; serialNum <= maxSerial; serialNum++) {
            String code = CodeUtil.generateCode(serialNum, coupon.getId());//参数1为优惠卷自增id值，参数2为优惠卷id，内部会计算出0-15之间的数字然后找密钥
            ExchangeCode exchangeCode = new ExchangeCode();
            exchangeCode.setId(serialNum); // 设置兑换码的id为自增id 主键生成策略改为input
            exchangeCode.setCode(code);
            exchangeCode.setExchangeTargetId(coupon.getId()); // 设置兑换码对应的优惠卷id
            exchangeCode.setExpiredTime(coupon.getIssueEndTime()); // 设置兑换码的过期时间

            list.add(exchangeCode); // 添加到列表中
        }

        // 将兑换码保存db exchange_code批量保存
        this.saveBatch(list);

        // 4.写入Redis缓存，member：couponId，score：兑换码的最大序列号
        redisTemplate.opsForZSet().add(COUPON_RANGE_KEY, coupon.getId().toString(), maxSerial);

    }

    @Override
    public boolean updateExchangeCodeMark(long serialNum, boolean flag) {
        String key = PromotionConstants.COUPON_CODE_MAP_KEY; // Redis中兑换码自增id的key

        // 修改兑换码自增id对应的offset的值
        Boolean aBoolean = redisTemplate.opsForValue().setBit(key, serialNum, flag);//返回的是之前的值

        return aBoolean != null && aBoolean; // 返回是否修改成功
    }
}
