package com.tianji.promotion.service.impl;


import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_MAP_KEY;
import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_SERIAL_KEY;


/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-12
 */
@Service
@Slf4j
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {


    private final StringRedisTemplate stringRedisTemplate;

    private BoundValueOperations<String, String> serialOps;

    public ExchangeCodeServiceImpl(StringRedisTemplate stringRedisTemplate1, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate1;
        this.serialOps = stringRedisTemplate.boundValueOps(COUPON_CODE_SERIAL_KEY);
    }

    @Override
    @Async("generateExchangeCodeExecutor")
    public void asyncGenerateExchangeCodes(Coupon coupon) {

        int total = coupon.getTotalNum();

        Long maxSerialNum = serialOps.increment(total);
        if (maxSerialNum == null) {
            return;
        }
        List<ExchangeCode> exchangeCodeList = new ArrayList<>(total);

        for (int serialNum = Math.toIntExact(maxSerialNum - total + 1); serialNum <= maxSerialNum; serialNum++) {


            String code = CodeUtil.generateCode(maxSerialNum, coupon.getId());

            ExchangeCode e = new ExchangeCode();
            e.setCode(code);
            e.setId(Math.toIntExact(maxSerialNum));
            e.setExchangeTargetId(coupon.getId());
            e.setExpiredTime(coupon.getIssueEndTime());
            exchangeCodeList.add(e);
        }
        saveBatch(exchangeCodeList);


    }

    @Override
    public boolean updateExchangeCodeMark(long serialNum, boolean flag) {
        Boolean boo = stringRedisTemplate.opsForValue().setBit(COUPON_CODE_MAP_KEY, serialNum, flag);
        return boo != null && boo;
    }

}