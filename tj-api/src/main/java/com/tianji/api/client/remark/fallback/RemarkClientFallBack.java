package com.tianji.api.client.remark.fallback;

import com.tianji.api.client.remark.RemarkClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Set;

@Slf4j
public class RemarkClientFallBack implements FallbackFactory<RemarkClient> {

    // 如果remark服务没启动或者网络异常等情况，Feign会调用这个方法，降级

    @Override
    public RemarkClient create(Throwable cause) {
        log.error("调用remark服务失败，降级处理: {}", cause.getMessage());

        return new RemarkClient() {
            @Override
            public Set<Long> getLikesStatusByBizIds(Set<Long> bizIds) {
                return null;
            }
        };
    }
}
