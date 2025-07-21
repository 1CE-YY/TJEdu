package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.hibernate.validator.constraints.pl.REGON;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {


    private final ICouponScopeService couponScopeService;

    private final ExchangeCodeServiceImpl exchangeCodeService; // 兑换码业务类

    private final IUserCouponService userCouponService; // 用户卷业务类

    private final StringRedisTemplate redisTemplate; // redis操作类

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        // 1. 将DTO转换为PO 保存优惠卷
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        this.save(coupon);

        // 2.判断是否限定范围 dto.specific false直接返回
        if (!dto.getSpecific()) {
            return;
        }

        List<Long> scopes = dto.getScopes();
        // 3.如果true 需要校验dto.scopes
        if (CollUtils.isEmpty(scopes)) {
            throw new BizIllegalException("限定分类范围不能为空");
        }

        // 4.保存优惠卷的限定范围 coupon_scope 批量新增
//        List<CouponScope> couponScopesList = new ArrayList<>();
//        for (Long scope : scopes) {
//            CouponScope couponScope = new CouponScope();
//            couponScope.setCouponId(coupon.getId());
//            couponScope.setBizId(scope);
//            couponScope.setType(1);
//            couponScopesList.add(couponScope);
//        }
        // stream流写法
        List<CouponScope> couponScopesList = scopes.stream()
                        .map(scope -> new CouponScope()
                                .setCouponId(coupon.getId())
                                .setBizId(scope)
                                .setType(1))
                        .collect(Collectors.toList());

        couponScopeService.saveBatch(couponScopesList);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query) {

        // 1. 分页查询优惠券Coupon
        Page<Coupon> page = this.lambdaQuery()
                .eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 2. 将查询结果转换为VO
        List<CouponPageVO> couponPageVOS = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page, couponPageVOS);
    }

    @Override
    public void issueCoupon(Long id, CouponIssueFormDTO dto) {

        log.debug("开始发放优惠券，线程名：{}", Thread.currentThread().getName());

        // 1.校验id
        if (id == null || !id.equals(dto.getId())) {
            throw new BadRequestException("非法参数，优惠券ID不能为空");
        }

        // 2.校验优惠券是否存在
        Coupon coupon = this.getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }

        // 3.校验优惠卷状态
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BizIllegalException("优惠券状态不正确，不能发放");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isBeginIssue = dto.getIssueBeginTime() == null || !dto.getIssueBeginTime().isAfter(now);//是否立刻发放
        // 4.修改优惠卷的领取开始和结束日期

        //方式1
//        if (isBeginIssue) {
//            coupon.setStatus(CouponStatus.ISSUING);
//            coupon.setIssueBeginTime(dto.getIssueBeginTime() == null ? now : dto.getIssueBeginTime());
//            coupon.setIssueEndTime(dto.getIssueEndTime());
//            coupon.setTermDays(dto.getTermDays());
//            coupon.setTermBeginTime(dto.getTermBeginTime());
//            coupon.setTermEndTime(dto.getTermEndTime());
//        } else {
//            coupon.setStatus(CouponStatus.UN_ISSUE);
//            coupon.setIssueBeginTime(dto.getIssueBeginTime() == null ? now : dto.getIssueBeginTime());
//            coupon.setIssueEndTime(dto.getIssueEndTime());
//            coupon.setTermDays(dto.getTermDays());
//            coupon.setTermBeginTime(dto.getTermBeginTime());
//            coupon.setTermEndTime(dto.getTermEndTime());
//        }
//
//
//        this.updateById(coupon);

        // 方式2
        Coupon couponDB = BeanUtils.copyBean(dto, Coupon.class);
        if (isBeginIssue) {
            couponDB.setStatus(CouponStatus.ISSUING);
            couponDB.setIssueBeginTime(now);
        } else {
            couponDB.setStatus(CouponStatus.UN_ISSUE);
        }

        this.updateById(couponDB);

        // 如果优惠卷是立即发放 将信息存入redis
        if (isBeginIssue) {
            String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
            /*redisTemplate.opsForHash().put(key, "issueBeginTime", String.valueOf(DateUtils.toEpochMilli(now)));
            redisTemplate.opsForHash().put(key, "issueEndTime", String.valueOf(DateUtils.toEpochMilli(dto.getIssueEndTime())));
            redisTemplate.opsForHash().put(key, "totalNum", String.valueOf(coupon.getTotalNum()));
            redisTemplate.opsForHash().put(key, "userLimit", String.valueOf(coupon.getUserLimit()));*/

            Map<String, String> map = new HashMap<>(4);
            map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
            map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
            map.put("totalNum", String.valueOf(coupon.getTotalNum()));
            map.put("userLimit", String.valueOf(coupon.getUserLimit()));
            // 2.写缓存
            redisTemplate.opsForHash().putAll(key, map);
        }

        // 5.如果优惠卷的领取方式为指定发放 且优惠卷之前的状态是等待发放
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(couponDB.getIssueEndTime());// 设置结束时间 从前端传来的
            exchangeCodeService.asyncGenerateExchangeCodes(coupon);// 异步生成兑换码

        }

    }

    /**
     * 查询发放中的优惠券列表
     * @return
     */
    @Override
    public List<CouponVO> queryIssuingCoupons() {
         // 1.查询db coupon 条件 方法中 手动领取
        List<Coupon> couponList = this.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }

        // 查询用户卷表user_coupon 条件 当前用户 正在发放中的卷
        Set<Long> couponIds = couponList.stream().map(Coupon::getId).collect(Collectors.toSet());//正在发放中的优惠卷id集合
        List<UserCoupon> list = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();// 当前用户针对正在发放的优惠卷列表数据

        // 统计当前用户对每一个卷的已领取的数量
        Map<Long, Long> issueMap = list.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 统计当前用户对每一个卷的已领取且未使用的数量
        Map<Long, Long> receivedMap = list.stream()
                .filter(userCoupon -> userCoupon.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 2.将查询结果转换为VO
        List<CouponVO> voList = new ArrayList<>();
        for (Coupon coupon : couponList) {
            CouponVO vo = BeanUtils.copyBean(coupon, CouponVO.class);
            // 是否可以领取：已经被领取的数量 < 优惠券总数量 && 当前用户已经领取的数量 < 每人限领数量
            Long issNum = issueMap.getOrDefault(coupon.getId(), 0L);
            boolean available = coupon.getIssueNum() < coupon.getTotalNum() && issNum.intValue() < coupon.getUserLimit();
            vo.setAvailable(available);
            // 是否可以使用：当前用户已经领取并且未使用的优惠券数量 > 0
            Long recNum = receivedMap.getOrDefault(coupon.getId(), 0L);
            boolean received = recNum > 0;
            vo.setReceived(received);// 是否可以使用
            voList.add(vo);
        }
        return voList;
    }
}
