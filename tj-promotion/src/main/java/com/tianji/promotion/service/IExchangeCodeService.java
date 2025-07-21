package com.tianji.promotion.service;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-12
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateExchangeCodes(Coupon coupon);

    boolean updateExchangeCodeMark(long serialNum, boolean flag);
}
