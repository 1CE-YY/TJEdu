package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.MyLockType;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.*;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private final StringRedisTemplate redisTemplate;

    private final RedissonClient redissonClient;

    private final RabbitMqHelper mqHelper;

    private final ICouponScopeService couponScopeService;

    private final Executor calculteSolutionExecutor;

    /**
     * 领取优惠券
     *
     * @param id 优惠券ID
     */
    @Override
    //@Transactional
    @MyLock(name = "lock:coupon:uid:#{id}")
    public void receiveCoupon(Long id) {
        // 1.根据id查询优惠卷相关信息做校验
        if (id == null) {
            throw new BadRequestException("优惠券ID不能为空");
        }
//        Coupon coupon = couponMapper.selectById(id);
        // 改为从redis中获取优惠券信息
        Coupon coupon = queryCouponByCache(id);

        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
//        if (coupon.getStatus() != CouponStatus.ISSUING) {
//            throw new BadRequestException("优惠券已失效");
//        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime())) {
            throw new BadRequestException("优惠券不在发放时间范围内");
        }
//        if (coupon.getTotalNum() <= 0 || coupon.getIssueNum() >= coupon.getTotalNum()) {
//            throw new BadRequestException("优惠券库存不足");
//        }
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        // 获取当前用户对该优惠卷已领取数量
        Long userId = UserContext.getUser();

        /*Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, id)
                .count();
        if (count >= coupon.getUserLimit()) {
            throw new BadRequestException("您已达到领取该优惠券上限");
        }
        // 2.优惠卷已发放数量加一
        couponMapper.incrIssueNum(id);

        // 3.生成用户卷
        saveUserCoupon(userId, coupon);*/
        //不加锁
//      checkAndCreateUserCoupon(userId, coupon, null);
        //jvm锁
//        synchronized (userId.toString().intern()) {
//            // 4.校验并生成用户卷
//            // 这里使用AOP暴露的代理对象，避免事务失效
//            IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
//            //checkAndCreateUserCoupon(userId, coupon, null);//这种写法是调用原来对象的方法
//            userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null); //这种写法是调用代理对象的方法 方法是有事务处理的
//        }
        //redis分布式锁
//        String key = "lock:coupon:uid:" + userId;
//        RedisLock redisLock = new RedisLock(key, redisTemplate);
//        try {
//            boolean isLock = redisLock.tryLock(5, TimeUnit.SECONDS);
//            if (!isLock) {
//                throw new BizIllegalException("操作太频繁，请稍后再试");
//            }
//            IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
//            userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);
//        } finally {
//            redisLock.unlock(); // 释放锁
//        }
        //redisson分布式锁
//        String key = "lock:coupon:uid:" + userId;
//        RLock lock = redissonClient.getLock(key);
//        try {
//            boolean isLock = lock.tryLock();//看门狗机制生效，默认30秒
//            if (!isLock) {
//                throw new BizIllegalException("操作太频繁，请稍后再试");
//            }
//            IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
//            userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);
//        }
//        finally {
//            lock.unlock(); // 释放锁
//        }

        //自定义注解基于redisson分布式锁
        //String key = "lock:coupon:uid:" + userId;
        /*IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
        userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);*/

        //统计已领取数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + id;
        // increment代表本次领取后的数量
        Long increment = redisTemplate.opsForHash().increment(key, userId.toString(), 1);

        //校验是否超过限领数量
        if (increment > coupon.getUserLimit()) {
            // 重置领取数量
            //redisTemplate.opsForHash().increment(key, userId.toString(), -1);//可以不重置
            throw new BadRequestException("您已达到领取该优惠券上限");
        }

        // 优惠卷库存减一
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        redisTemplate.opsForHash().increment(couponKey, "totalNum", -1);

        // 发送消息到mq
        UserCouponDTO msg = new UserCouponDTO();
        msg.setUserId(userId);
        msg.setCouponId(id);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, msg);

    }

    /**
     * 从redis缓存中查询优惠券信息
     *
     * @param id 优惠券ID
     * @return 优惠券信息
     */
    private Coupon queryCouponByCache(Long id) {
        // 拼接缓存key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        Map<Object, Object> objMap = redisTemplate.opsForHash().entries(key);
        if (objMap.isEmpty()) {
            return null;
        }
        // 3.数据反序列化
        return BeanUtils.mapToBean(objMap, Coupon.class, false, CopyOptions.create());
    }

    /**
     * 核销优惠券
     *
     * @param code 兑换码
     */
    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        // 1.校验code
        if (StringUtils.isBlank(code)) {
            throw new BadRequestException("兑换码不能为空");
        }

        // 2.解析兑换码得到自增id
        long serialNum = CodeUtil.parseCode(code);

        // 3.判断兑换码是否已兑换 采用redis的bitmap结构
        boolean result = exchangeCodeService.updateExchangeCodeMark(serialNum, true);
        if (result) {
            throw new BadRequestException("兑换码已被使用");
        }

        try {
            // 4.根据自增id查询优惠券信息
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            if (exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在");
            }

            // 5.判断优惠券是否过期
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiredTime = exchangeCode.getExpiredTime();
            if (now.isAfter(expiredTime)) {
                throw new BizIllegalException("兑换码已过期");
            }

            // 校验并生成用户卷
            Long userId = UserContext.getUser();
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            if (coupon == null) {
                throw new BizIllegalException("兑换码对应的优惠券不存在");
            }
            checkAndCreateUserCoupon(userId, coupon, serialNum);
            // 6.判断是否超出限领数量
            // 7.优惠券已发放数量加一
            // 8.生成用户卷
            // 9.将兑换码标记为已兑换
            //已经封装到上面的函数
        } catch (Exception e) {
            // 重置兑换码
            exchangeCodeService.updateExchangeCodeMark(serialNum, false);
            throw e;
        }

    }

    @Transactional
    @Override
    @MyLock(name = "lock:coupon:uid:#{userId}",
            waitTime = 1,
            leaseTime = 5,
            unit = TimeUnit.SECONDS,
            lockType = MyLockType.RE_ENTRANT_LOCK, lockStrategy = MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT)
//记得要先获取锁再开启事务 可在 MyLockAspect中实现order接口，赋值
    public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {

        //Long类型-128~127之间是同一个对象 超过该区间则是不同的对象
        //Long.toString方法底层是newString，所以还是不同的对象
        //Long.toString.intern()intern方法是强制从常量池中取字符串

        //synchronized (userId.toString().intern()) {
        // 获取当前用户对该优惠卷已领取数量
        Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        if (count >= coupon.getUserLimit()) {
            throw new BadRequestException("您已达到领取该优惠券上限");
        }
        // 2.优惠卷已发放数量加一
        couponMapper.incrIssueNum(coupon.getId());

        // 3.生成用户卷
        saveUserCoupon(userId, coupon);
        if (serialNum != null) {
            // 4.更新兑换码的标记为已兑换
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId, userId)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
        //}

    }

    //    @MyLock(name = "lock:coupon:uid:#{userId}",
//            waitTime = 1,
//            leaseTime = 5,
//            unit = TimeUnit.SECONDS,
//            lockType = MyLockType.RE_ENTRANT_LOCK, lockStrategy = MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT)
    @Transactional
    @Override
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {
        // 1.查询优惠券
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if (coupon == null) {
            return;
        }
        // 2.更新优惠券的已经发放的数量 + 1
        int num = couponMapper.incrIssueNum(coupon.getId());
        if (num == 0) {
            throw new BizIllegalException("优惠券库存不足！");
        }
        // 3.新增一个用户券
        saveUserCoupon(msg.getUserId(), coupon);

    }



    /**
     * 保存用户卷
     *
     * @param userId 用户ID
     * @param coupon 优惠券信息
     */
    private void saveUserCoupon(Long userId, Coupon coupon) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();// 优惠券的有效期
        if (termBeginTime == null && termEndTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        userCoupon.setTermBeginTime(termBeginTime);
        userCoupon.setTermEndTime(termEndTime);
        this.save(userCoupon);
    }

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
        // 1.查询当前用户可用优惠卷coupon和user_coupon 条件userId status = 1 优惠卷的规则 优惠卷的id 用户卷的id
        List<Coupon> coupons = this.baseMapper.queryMyCoupons(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        log.debug("查询到用户可用的优惠券数量: {}", coupons.size());

        // 2.初筛
        // 计算订单总金额
        double totalAmount = courses.stream()
                .mapToDouble(OrderCourseDTO::getPrice)
                .sum();
        log.debug("订单总金额: {}", totalAmount);

        // 校验优惠卷是否可用
        /*List<Coupon> availableCoupons = new ArrayList<>();
        for (Coupon coupon : coupons) {
            boolean flag = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse((int) totalAmount, coupon);
            if (flag) {
                availableCoupons.add(coupon);
            }
        }*/
        // stream流写法
        List<Coupon> availableCoupons = coupons.stream()
                .filter(coupon -> DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse((int) totalAmount, coupon))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) {
            return CollUtils.emptyList();
        }
        log.debug("初筛后可用优惠券数量: {}", availableCoupons.size());

        // 3.细筛 需要考虑优惠卷的限定范围
        Map<Coupon, List<OrderCourseDTO>> avaMap = findAvailableCoupons(availableCoupons, courses);
        if (avaMap.isEmpty()) {
            return CollUtils.emptyList();
        }
        availableCoupons = new ArrayList<>(avaMap.keySet());
        log.debug("细筛后可用优惠券数量: {}", availableCoupons.size());

        // 排列组合
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        for (Coupon availableCoupon : availableCoupons) {
            solutions.add(List.of(availableCoupon));//添加单卷
        }

        // 4.计算每种组合的优惠明细
        /*log.debug("开始计算每种组合的优惠明细");
        //List<CouponDiscountDTO> list = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        List<CouponDiscountDTO> dtos = new ArrayList<>(solutions.size());
        for (List<Coupon> solution : solutions) {
            CouponDiscountDTO dto = calculateSolutionDiscount(avaMap, courses, solution);
            log.debug("计算优惠明细: {}", dto);
            dtos.add(dto);
        }*/

        //使用多线程改造第四步 并行计算每种优惠明细
        log.debug("开始并行计算每种组合的优惠明细");
        //List<CouponDiscountDTO> dtos = new ArrayList<>();
        List<CouponDiscountDTO> dtos = Collections.synchronizedList(new ArrayList<>(solutions.size()));//线程安全的集合
        CountDownLatch latch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions) {
            CompletableFuture.supplyAsync(() -> {
                CouponDiscountDTO dto = calculateSolutionDiscount(avaMap, courses, solution);
                log.debug("计算优惠明细: {}", dto);
                return dto;
            }, calculteSolutionExecutor).thenAccept(dto -> {
                if (dto != null) {
                    log.debug("计算优惠明细成功: {}", dto);
                    dtos.add(dto);
                }
                latch.countDown(); // 计数器减一
            }).exceptionally(ex -> {
                log.error("计算优惠明细异常: {}", ex.getMessage());
                latch.countDown(); // 异常也要减一
                return null;
            });
        }
        try {
            latch.await(2, TimeUnit.SECONDS); // 等待所有线程完成
        } catch (InterruptedException e) {
            log.error("等待线程完成时被中断: {}", e.getMessage());
            throw new RuntimeException(e);
        }


        // 5.筛选最优解
        // 用卷相同时，保留优惠金额最高的方案
        // 金额相同时，保留用卷最少的方案
        return findBestSolution(dtos);

    }

    /**
     * 查找最优解
     * 用卷相同时，保留优惠金额最高的方案
     * 金额相同时，保留用卷最少的方案
     *
     * @param solutions 优惠明细列表
     * @return 最优解列表
     */
    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> solutions) {
        // 1.创建两个map 分别记录上述两个条件
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>(); // 用卷相同，优惠金额最高的方案
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>(); // 优惠金额相同，用卷最少的方案

        // 2.循环方案 向map中记录方案
        for (CouponDiscountDTO solution : solutions) {
            // 2.1 对优惠卷id 升序排序 转字符串 然后逗号拼接
            String ids = solution.getIds().stream().sorted(Comparator.comparing(Long::longValue)).map(String::valueOf).collect(Collectors.joining(","));

            // 2.2 从moreDiscountMap中取出旧的记录 与当前方案比较
            CouponDiscountDTO oldMoreDiscount = moreDiscountMap.get(ids);
            if (oldMoreDiscount != null && oldMoreDiscount.getDiscountAmount() >= solution.getDiscountAmount()) {
                // 当前方案优惠金额少，跳过
                continue;
            }

            // 2.3 从lessCouponMap中取出旧的记录 与当前方案比较
            CouponDiscountDTO oldLessCoupon = lessCouponMap.get(solution.getDiscountAmount());
            int oldLessCouponSize = oldLessCoupon != null ? oldLessCoupon.getIds().size() : Integer.MAX_VALUE;// 旧方案用卷数量
            int newLessCouponSize = solution.getIds().size();// 当前方案用卷数量
            if (oldLessCoupon != null && newLessCouponSize > 1 && oldLessCouponSize <= newLessCouponSize) {
                // 当前方案用卷数量多，跳过
                continue;
            }

            // 2.4 如果当前方案更优 则更新map中的记录
            moreDiscountMap.put(ids, solution); // 更新用卷相同，优惠金额最高的方案
            lessCouponMap.put(solution.getDiscountAmount(), solution); // 更新优惠金额相同，用卷最少的方案

        }

        // 3.求两个map的交集
        Collection<CouponDiscountDTO> bestSolution = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());

        // 4.对最终方案结果按优惠金额倒叙
        List<CouponDiscountDTO> latestBestSolution = bestSolution.stream()
                .sorted(Comparator.comparing(CouponDiscountDTO::getDiscountAmount, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        return latestBestSolution;
    }

    /**
     * 计算每种优惠组合的优惠明细
     * @param avaMap   可用优惠券与对应课程的映射关系
     * @param courses  订单课程列表
     * @param solution 优惠券组合
     * @return 优惠明细
     */
    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> avaMap, List<OrderCourseDTO> courses, List<Coupon> solution) {
        // 1.创建方案dto对象
        CouponDiscountDTO dto = new CouponDiscountDTO();

        // 2.初始化商品id和折扣明细的映射
        Map<Long, Integer> detailMap = courses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, orderCourseDTO -> 0));

        // 3.计算每种优惠券的折扣明细
        // 3.1循环方案中优惠卷
        for (Coupon coupon : solution) {
            // 3.2取出每个优惠卷对应的可用课程
            List<OrderCourseDTO> availiableCourses = avaMap.get(coupon);
            if (CollUtils.isEmpty(availiableCourses)) {
                continue; // 如果没有可用课程则跳过该优惠卷
            }

            // 3.3计算该优惠卷可用课程的总折扣金额 商品价格-该商品的折扣明细
            int totalAmount = availiableCourses.stream()
                    .mapToInt(value -> value.getPrice() - detailMap.getOrDefault(value.getId(), 0))
                    .sum(); // 代表该优惠卷可用的总金额

            // 3.4判断优惠卷是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)) {
                continue; // 如果不可用则跳过该优惠卷
            }
            // 3.5计算该优惠卷使用后的折扣值
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            // 3.6更新商品的折扣明细
            calculateDetailDiscount(detailMap, availiableCourses, totalAmount, discountAmount);
            // 3.7累加每一个优惠卷的优惠金额赋值给结果dto对象
            dto.getIds().add(coupon.getId());//只要执行到当前行，说明该优惠卷是可用的
            dto.setDiscountAmount(dto.getDiscountAmount() + discountAmount);
            dto.getRules().add(discount.getRule(coupon));
        }

        return dto;
    }

    /**
     * 计算每个商品的折扣明细
     *
     * @param detailMap         商品id和折扣明细的映射
     * @param availiableCourses 可用课程列表
     * @param totalAmount       可用课程的总金额
     * @param discountAmount    折扣金额
     */
    private void calculateDetailDiscount(Map<Long, Integer> detailMap, List<OrderCourseDTO> availiableCourses, int totalAmount, int discountAmount) {
        int times = 0;//已处理的商品个数
        int remainDiscount = discountAmount; // 剩余的折扣金额
        for (OrderCourseDTO c : availiableCourses) {
            times++;
            int discount = 0;
            if (times == availiableCourses.size()) {
                // 说明是最后一个课程
                // 将剩余的折扣金额全部分配给最后一个课程
                 discount = remainDiscount;
            } else {
                // 不是最后一个课程，按比例分配折扣金额
                discount = c.getPrice() * discountAmount / totalAmount ;//先乘再除，避免精度问题
                remainDiscount -= discount; // 减去已分配的折扣金额
            }
            // 更新商品的折扣明细 既是添加到detailMap中
            detailMap.put(c.getId(), detailMap.getOrDefault(c.getId(), 0) + discount);
        }
    }

    /**
     * 细筛查找可用的优惠券
     *
     * @param coupons 初筛后可用的优惠券列表
     * @param orderCourses          订单课程列表
     * @return 可用优惠券与对应课程的映射关系
     */
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupons(List<Coupon> coupons, List<OrderCourseDTO> orderCourses) {

        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>();

        // 1.循环遍历初筛后的优惠卷集合
        for (Coupon coupon : coupons) {
            List<OrderCourseDTO> availableCourses = orderCourses;
            // 2.筛选出每一个优惠卷的符合限定范围的可用课程
            // 2.1 判断优惠卷是否限定范围
            if (coupon.getSpecific()) {
                // 2.2 如果限定范围则查询限定范围coupon_scope字段
                List<CouponScope> scopeList = couponScopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId())
                        .list();

                // 2.3得到限定范围的id集合
                List<Long> scopeIds = scopeList.stream()
                        .map(CouponScope::getBizId)
                        .collect(Collectors.toList());

                // 2.4 筛选出符合限定范围的课程
                availableCourses = orderCourses.stream()
                        .filter(course -> scopeIds.contains(course.getCateId()))
                        .collect(Collectors.toList());

            }
            if (CollUtils.isEmpty(availableCourses)) {
                // 如果没有符合限定范围的课程则跳过该优惠卷
                continue;
            }

            // 3.计算该优惠卷可用的总金额
            int totalAmount = availableCourses.stream()
                    .mapToInt(OrderCourseDTO::getPrice)
                    .sum();//代表该优惠卷可用的总金额

            // 4.判断是否可用 如果可用则放入map中
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmount, coupon)) {
                // 5.放入map中
                map.put(coupon, availableCourses);
            }
        }

        return map;
    }
}
