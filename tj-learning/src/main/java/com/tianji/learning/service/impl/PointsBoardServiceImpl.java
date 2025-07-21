package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.spel.spi.ReactiveEvaluationContextExtension;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author 1CE-YY
 * @since 2025-07-11
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;

    private final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardList(PointsBoardQuery query) {

        // 1.获取当前登录的用户id
        Long userId = UserContext.getUser();

        // 2.判断是查当前赛季还是历史赛季
        boolean isCurrent = query.getSeason() == null || query.getSeason() == 0; //true则查询当前赛季 redis
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;

        Long season = query.getSeason(); //历史赛季id


//        if (isCurrent) {
//            queryMyCurrentBoard(key);
//        } else {
//            queryMyHistoryBoard(season);
//        }



        // 3.查询我的排名和积分 根据赛季区分 判断是查redis还是db
        PointsBoard board = isCurrent ? queryMyCurrentBoard(key) : queryMyHistoryBoard(season);

        // 4.分页查询赛季列表 也要根据赛季区分
        List<PointsBoard> list = isCurrent ? queryCurrentBoard(key, query.getPageNo(), query.getPageSize()) : queryHistoryBoard(query);


        // 5.封装返回结果
        PointsBoardVO vo = new PointsBoardVO();
        vo.setRank(board.getRank() == null ? 0 : board.getRank() ); // 如果没有排名，默认为0
        vo.setPoints(board.getPoints() == null ? 0 : board.getPoints()); // 如果没有分值，默认为0

        // 封装用户id集合 远程调用用户服务 获取用户信息 转map
        Set<Long> userIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        if (CollUtils.isEmpty(users)) {
            throw new BizIllegalException("用户信息不存在，请检查用户服务是否正常！");
        }
        Map<Long, String> userDtoMap = users.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));

        List<PointsBoardItemVO> itemVOS = new ArrayList<>();
        for (PointsBoard item : list) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            itemVO.setName(userDtoMap.get(item.getUserId()));
            itemVO.setPoints(item.getPoints());
            itemVO.setRank(item.getRank());
            itemVOS.add(itemVO);
        }

        vo.setBoardList(itemVOS);
        return vo;

    }



    /** 查询历史赛季的学霸天梯榜
     * @return 历史赛季的学霸天梯榜
     */
    public List<PointsBoard> queryHistoryBoard(PointsBoardQuery query) {
        // TODO
        return null;
    }


    /** 查询当前赛季的学霸天梯榜
     * @param key redis中的key
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 当前赛季的学霸天梯榜
     */
    public List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize) {
        // 1.计算start和end 分页值
        int start = (pageNo - 1) * pageSize; // 起始索引
        int end = start + pageSize - 1; // 结束索引

        // 2.利用redis中的zrevrange命令 会按照分数倒序 分页查询
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (CollUtils.isEmpty(typedTuples)) {
            return CollUtils.emptyList(); // 如果没有数据，返回空列表
        }

        // 3.将查询结果转换为PointsBoard对象列表
        int rank = start + 1; // 排名从每页的1开始
        List<PointsBoard> boards = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String value = typedTuple.getValue();
            Double score = typedTuple.getScore();
            if (StringUtils.isBlank(value) || score == null) {
                continue; // 跳过无效数据
            }
            PointsBoard board = new PointsBoard();
            board.setUserId(Long.valueOf(value)); // 设置用户ID
            board.setPoints(score.intValue()); // 设置分值
            board.setRank(rank++); // 设置排名

            boards.add(board); // 添加到结果列表
        }



        return boards;
    }

    /** 查询我的历史赛季学霸天梯榜
     * @param season 赛季id
     * @return 我的历史赛季学霸天梯榜
     */
    public PointsBoard queryMyHistoryBoard(Long season) {

        return null;
    }


    /** 查询当前赛季的学霸天梯榜
     * @param key redis中的key
     * @return 当前赛季的学霸天梯榜
     */
    public PointsBoard queryMyCurrentBoard(String key) {

        Long userId = UserContext.getUser();

        // 获取分值
        Double score = redisTemplate.opsForZSet().score(key, userId);

        // 获取排名
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());

        PointsBoard board = new PointsBoard();

        board.setRank(rank == null ? 0 : rank.intValue() + 1); // 排名从1开始
        board.setPoints(score == null ? 0 : score.intValue()); // 分值可能为null，默认为0

        return board;
    }



}
