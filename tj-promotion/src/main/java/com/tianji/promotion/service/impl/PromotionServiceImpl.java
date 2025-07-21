package com.tianji.promotion.service.impl;

import com.tianji.promotion.domain.po.Promotion;
import com.tianji.promotion.mapper.PromotionMapper;
import com.tianji.promotion.service.IPromotionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 促销活动，形式多种多样，例如：优惠券 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-12
 */
@Service
public class PromotionServiceImpl extends ServiceImpl<PromotionMapper, Promotion> implements IPromotionService {

}
