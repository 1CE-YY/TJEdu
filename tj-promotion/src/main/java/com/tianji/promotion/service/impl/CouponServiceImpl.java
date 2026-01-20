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
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.hibernate.validator.constraints.pl.REGON;
import org.springframework.context.annotation.Bean;
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

    private final IExchangeCodeService exchangeCodeService;

    private final IUserCouponService userCouponService;

    private final CouponMapper couponMapper;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        Coupon coupon = BeanUtils.toBean(dto, Coupon.class);
        save(coupon);

        if (dto.getSpecific() == null) {
            return;
        }
        List<Long> scopes = dto.getScopes();

        if (CollUtils.isEmpty(scopes)) {
            throw new BizIllegalException("优惠券作用范围不能为空");
        }

        List<CouponScope> scopeList = scopes.stream()
                .map(item -> new CouponScope().setBizId(item).setCouponId(coupon.getId()))
                .collect(Collectors.toList());

        couponScopeService.saveBatch(scopeList);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query) {
        Integer status = query.getStatus();
        String name = query.getName();
        Integer type = query.getType();
        Page<Coupon> page = lambdaQuery()
                .eq(type != null, Coupon::getDiscountType, query.getType())
                .eq(status != null, Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotBlank(name), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> voList = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page, voList);
    }

    @Override
    public void issueCoupon(Long id, CouponIssueFormDTO dto) {
        Coupon coupon = getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        if (!Objects.equals(coupon.getStatus(), CouponStatus.DRAFT) && !Objects.equals(coupon.getStatus(), CouponStatus.UN_ISSUE)) {
            throw new BizIllegalException("优惠券状态错误，无法发放");
        }
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        boolean isBegin = (issueBeginTime == null) || !issueBeginTime.isAfter(now);
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        if (isBegin) {
            c.setStatus(CouponStatus.ISSUING);
            c.setIssueBeginTime(now);
        } else {
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        updateById(c);

        if (Objects.equals(coupon.getObtainWay(), ObtainType.ISSUE) && Objects.equals(coupon.getStatus(), CouponStatus.DRAFT)) {
            // 发放优惠券
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            exchangeCodeService.asyncGenerateExchangeCodes(coupon);


        }
    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(coupons)) {
            return Collections.emptyList();
        }

        List<Long> couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());

        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();

        Map<Long, Long> issueMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        Map<Long, Long> unusedMap = userCoupons.stream()
                .filter(uc -> Objects.equals(uc.getStatus(), UserCouponStatus.UNUSED))
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        List<CouponVO> list = new ArrayList<>(coupons.size());
        for (Coupon coupon : coupons) {
            CouponVO vo = BeanUtils.toBean(coupon, CouponVO.class);

            vo.setAvailable(coupon.getIssueNum() < coupon.getTotalNum() && issueMap.getOrDefault(coupon.getId(), 0L) < coupon.getUserLimit());
            vo.setReceived(unusedMap.getOrDefault(coupon.getId(), 0L) > 0L);
            list.add(vo);
        }

        return list;

    }

    @Override
    public void incrementIssueNum(Long couponId) {
        couponMapper.incrementIssueNum(couponId);
    }
}
