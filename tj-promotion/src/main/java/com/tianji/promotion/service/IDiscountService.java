package com.tianji.promotion.service;

import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;

import java.util.List;

public interface IDiscountService {
    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses);
}
