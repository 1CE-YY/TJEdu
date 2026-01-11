package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<LearningRecordMapper> recordMapper;
    private final ILearningLessonService lessonService;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private static volatile boolean begin = true;

    // 声明线程池（改为实例变量，便于生命周期管理）
    private ThreadPoolExecutor poolExecutor;

    private ThreadPoolExecutor createThreadPoolExecutor() {
        return new ThreadPoolExecutor(
                10, // 核心线程数
                16, // 最大线程数
                60L, // 空闲线程存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000), // 使用有界队列，增大容量避免任务频繁拒绝
                new ThreadFactory() {
                    private int count = 1;
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "learning-record-delay-task-" + count++);
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程执行，避免任务丢失
        );
    }


    // 项目启动后 当前实例化 属性输入后 方法就会运行 一般用来初始化工作
    @PostConstruct
    public void init(){
        // 初始化线程池
        poolExecutor = createThreadPoolExecutor();
        // 启动延迟任务处理线程
        CompletableFuture.runAsync(this::handleDelayTask);
    }
    // 项目销毁前执行，通常用来释放资源
    @PreDestroy
    public void destroy(){
        begin = false;
        log.debug("延迟任务停止执行！");

        // 优雅关闭线程池
        if (poolExecutor != null && !poolExecutor.isShutdown()) {
            try {
                // 停止接受新任务
                poolExecutor.shutdown();
                // 等待已提交的任务完成，最多等待30秒
                if (!poolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("线程池未能在30秒内完成关闭，强制关闭");
                    // 强制关闭
                    poolExecutor.shutdownNow();
                    // 等待任务响应中断
                    if (!poolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("线程池强制关闭失败");
                    }
                }
                log.debug("线程池已关闭");
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                poolExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void handleDelayTask(){
        while (begin) {
            try {
                // 1.获取到期的延迟任务
                DelayTask<RecordTaskData> task = queue.take();// 阻塞式获取

                // 2.提交任务到线程池，并捕获异常
                poolExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            RecordTaskData data = task.getData();
                            // 2.查询Redis缓存
                            LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                            log.debug("处理延迟任务，数据：{}, 缓存中的数据：{}", data, record);
                            if (record == null) {
                                return;
                            }
                            // 3.比较数据，moment值
                            if(!Objects.equals(data.getMoment(), record.getMoment())) {
                                // 不一致，说明用户还在持续提交播放进度，放弃旧数据
                                return;
                            }

                            // 4.一致，持久化播放进度数据到数据库
                            // 4.1.更新学习记录的moment
                            record.setFinished(null);
                            recordMapper.getObject().updateById(record);
                            // 4.2.更新课表最近学习信息
                            LearningLesson lesson = new LearningLesson();
                            lesson.setId(data.getLessonId());
                            lesson.setLatestSectionId(data.getSectionId());
                            lesson.setLatestLearnTime(LocalDateTime.now());
                            lessonService.updateById(lesson);
                            log.debug("学习记录持久化成功，lessonId:{}, sectionId:{}", data.getLessonId(), data.getSectionId());
                        } catch (Exception e) {
                            log.error("执行延迟任务异常，任务数据：{}", task.getData(), e);
                        }
                    }
                });

                log.debug("准备持久化学习记录");
            } catch (InterruptedException e) {
                log.warn("延迟任务处理线程被中断");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("处理延迟任务发生异常", e);
            }
        }
    }

    public void addLearningRecordTask(LearningRecord record){
        // 1.添加数据到Redis缓存
        writeRecordCache(record);
        // 2.提交延迟任务到延迟队列 DelayQueue
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            // 1.数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 2.写入Redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            // 3.添加缓存过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常", e);
        }
    }

    public LearningRecord readRecordCache(Long lessonId, Long sectionId){
        try {
            // 1.读取Redis数据
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (cacheData == null) {
                return null;
            }
            // 2.数据检查和转换
            return JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存读取异常", e);
            return null;
        }
    }

    /**
     * 清除学习记录缓存
     * @param lessonId
     * @param sectionId
     */
    public void cleanRecordCache(Long lessonId, Long sectionId){
        // 删除数据
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash().delete(key, sectionId.toString());
    }

    @Data
    @NoArgsConstructor
    private static class RecordCacheData{
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }
    @Data
    @NoArgsConstructor
    private static class RecordTaskData{
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
}