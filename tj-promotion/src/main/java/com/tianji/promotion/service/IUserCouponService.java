package com.tianji.promotion.service;

import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-13
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long id);

    void exchangeCoupon(String code);

    void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum);

    void checkAndCreateUserCouponNew(UserCouponDTO msg);

    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses);
}
