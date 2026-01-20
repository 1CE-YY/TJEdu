package com.tianji.promotion.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    private final IExchangeCodeService exchangeCodeService;

    @Override
    public void receiveCoupon(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BizIllegalException("不在领取时间范围内，无法领取");
        }

        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BizIllegalException("优惠券已被领完，无法领取");
        }

        Long userId = UserContext.getUser();
        synchronized (userId.toString().intern()) {
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreateUserCoupon(coupon, userId);
        }


    }

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId) {

        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BizIllegalException("您已达到该优惠券的领取上限，无法继续领取");
        }
        int row = couponMapper.incrementIssueNum(coupon.getId());
        if (row <= 0) {
            throw new BizIllegalException("优惠券已被领完，无法领取");
        }

        saveUserCoupon(coupon, userId);


    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(coupon.getId());
        uc.setUserId(userId);

        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        save(uc);
    }

    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        long serialNum = CodeUtil.parseCode(code);

        boolean exchanged = exchangeCodeService.updateExchangeCodeMark(serialNum, true);

        if (exchanged) {
            throw new BizIllegalException("该兑换码已被使用，无法兑换");
        }
        try {
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            if (exchangeCode == null) {
                throw new BadRequestException("无效兑换码");
            }
            if (exchangeCode.getExpiredTime() != null && exchangeCode.getExpiredTime().isBefore(LocalDateTime.now())) {
                throw new BizIllegalException("该兑换码已过期，无法兑换");
            }
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            if (coupon == null || !Objects.equals(coupon.getStatus(), CouponStatus.ISSUING)) {
                throw new BizIllegalException("该兑换码对应的优惠券不可用，无法兑换");
            }
            checkAndCreateUserCoupon(coupon, UserContext.getUser());

            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId, UserContext.getUser())
                    .eq(ExchangeCode::getId, serialNum)
                    .update();

        } catch (Exception e) {
            exchangeCodeService.updateExchangeCodeMark(serialNum, false);
            throw e;
        }

    }

    @Override
    public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {

    }

    @Override
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {

    }

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
        return Collections.emptyList();
    }
}
