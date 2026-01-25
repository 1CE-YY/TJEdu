package com.tianji.promotion.service.impl;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountServiceImpl implements IDiscountService {

    private final UserCouponMapper userCouponMapper;

    private final ICouponScopeService couponScopeService;

    private final Executor discountSolutionExecutor;

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
        // 1.查询我的所有可用优惠券
        List<Coupon> coupons = userCouponMapper.queryMyCoupons(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)) {
            return List.of();
        }

        // 2.初筛
        // 2.1计算订单总价格
        double totalPrice = orderCourses.stream()
                .mapToDouble(OrderCourseDTO::getPrice)
                .sum();
        // 2.2过滤不满足使用条件的优惠券
        List<Coupon> availableCoupons = coupons.stream()
                .filter(coupon -> DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse((int) totalPrice, coupon))
                .collect(Collectors.toList());

        if (CollUtils.isEmpty(availableCoupons)) {
            return List.of();
        }
        // 3.排列组合出所有方案
        // 3.1细筛
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(availableCoupons, orderCourses);

        if (CollUtils.isEmpty(availableCouponMap)) {
            return List.of();
        }

        // 3.2 组合方案
        availableCoupons = new ArrayList<>(availableCouponMap.keySet());

        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);

        // 3.3 添加单独的优惠券方案
        for (Coupon coupon : availableCoupons) {
            solutions.add(List.of(coupon));
        }

        // 4.计算每个方案的优惠金额
        List<CouponDiscountDTO> discountSolutions = Collections.synchronizedList(new ArrayList<>(solutions.size()));


        CountDownLatch latch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions) {
            // 异步计算每个方案的优惠金额
            CompletableFuture.supplyAsync(() -> {
                try {
                    return calculateSolutionDiscount(availableCouponMap, orderCourses, solution);
                } finally {
                    latch.countDown();
                }
            }, discountSolutionExecutor).thenAccept(discountSolutions::add);
        }
        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("计算优惠方案超时", e);
            Thread.currentThread().interrupt();
        }


        // 5.筛选最优方案


        return findBestSolution(discountSolutions);
    }

    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> discountSolutions) {
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();
        for (CouponDiscountDTO solution : discountSolutions) {
            String ids = solution.getIds().stream()
                    .sorted(Long::compare)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            CouponDiscountDTO best = moreDiscountMap.get(ids);
            if (best != null && best.getDiscountAmount() >= solution.getDiscountAmount()) {
                continue;
            }
            best = lessCouponMap.get(solution.getDiscountAmount());
            if (solution.getIds().size() > 1 && best != null && best.getDiscountAmount() < solution.getDiscountAmount()) {
                continue;
            }
            moreDiscountMap.put(ids, solution);
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }
        Collection<CouponDiscountDTO> bestSolutions = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());

        return bestSolutions.stream().sorted(Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount).reversed()).collect(Collectors.toList());
    }

    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> availableCouponMap, List<OrderCourseDTO> orderCourses, List<Coupon> solution) {

        CouponDiscountDTO dto = new CouponDiscountDTO();

        Map<Long, Integer> detailedMap = orderCourses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, c -> 0));
        for (Coupon coupon : solution) {
            List<OrderCourseDTO> availableCourses = availableCouponMap.get(coupon);
            if (CollUtils.isEmpty(availableCourses)) {
                continue;
            }
            int totalNum = availableCourses.stream()
                    .mapToInt(oc -> oc.getPrice() - detailedMap.get(oc.getId()))
                    .sum();
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalNum, coupon)) {
                continue;
            }
            int discountAmount = discount.calculateDiscount(totalNum, coupon);
            // 计算明细
            calculateDiscountDetail(detailedMap, availableCourses, totalNum, discountAmount);
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(dto.getDiscountAmount() + discountAmount);
        }

        return dto;
    }

    private void calculateDiscountDetail(Map<Long, Integer> detailedMap, List<OrderCourseDTO> availableCourses, int totalNum, int discountAmount) {
        int times = 0;
        int remainingDiscount = discountAmount;
        for (OrderCourseDTO course : availableCourses) {
            times++;
            int discount = 0;
            if (times == availableCourses.size()) {
                // 最后一个课程，直接用剩余的优惠金额，避免因四舍五入导致的误差
                discount = remainingDiscount;
                detailedMap.put(course.getId(), detailedMap.get(course.getId()) + discount);
            } else {
                discount = course.getPrice() * discountAmount / totalNum;
                remainingDiscount -= discount;

            }
            detailedMap.put(course.getId(), detailedMap.get(course.getId()) + discount);

        }
    }

    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(List<Coupon> coupons, List<OrderCourseDTO> orderCourses) {
        Map<Coupon, List<OrderCourseDTO>> result = new HashMap<>();
        for (Coupon coupon : coupons) {
            List<OrderCourseDTO> applicableCourses = orderCourses;
            if (coupon.getSpecific()) {
                List<CouponScope> scopes = couponScopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId())
                        .list();
                if (CollUtils.isEmpty(scopes)) {
                    continue;
                }
                Set<Long> scopeIds = scopes.stream()
                        .map(CouponScope::getBizId)
                        .collect(Collectors.toSet());
                applicableCourses = orderCourses.stream()
                        .filter(course -> scopeIds.contains(course.getCateId()))
                        .collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(applicableCourses)) {
                continue;
            }
            int totalNum = applicableCourses.stream()
                    .mapToInt(OrderCourseDTO::getPrice)
                    .sum();
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalNum, coupon)) {
                result.put(coupon, applicableCourses);
            }
        }
        return result;
    }
}
