package com.tianji.api.client.remark;



import com.tianji.api.client.remark.fallback.RemarkClientFallBack;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

@FeignClient(name = "remark-service", fallbackFactory = RemarkClientFallBack.class) // Feign客户端被调用者名称
public interface RemarkClient {

    @GetMapping("/likes/list")
    public Set<Long> getLikesStatusByBizIds(@RequestParam("bizIds") Set<Long> bizIds);
}
