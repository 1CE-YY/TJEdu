package com.tianji.learning.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.DelayQueue;

import static org.junit.jupiter.api.Assertions.*;
@Slf4j
class DelayTaskTest {

    @Test
    public void testDelayQueue() throws  InterruptedException {
        DelayQueue<DelayTask<String>> delayQueue = new DelayQueue<>();
        delayQueue.put(new DelayTask<>("task1", java.time.Duration.ofSeconds(3)));
        delayQueue.put(new DelayTask<>("task2", java.time.Duration.ofSeconds(1)));
        delayQueue.put(new DelayTask<>("task3", java.time.Duration.ofSeconds(2)));

        while (!delayQueue.isEmpty()) {
            DelayTask<String> task = delayQueue.take();
            log.info("Executed: {}", task.getData());
        }
    }
  
}