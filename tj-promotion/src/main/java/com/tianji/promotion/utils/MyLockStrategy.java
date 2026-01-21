package com.tianji.promotion.utils;

import org.redisson.api.RLock;

public enum MyLockStrategy {
    SKIP_FAST() {
        @Override
        public boolean tryLock(RLock lock, MyLock properties) throws InterruptedException {

            return lock.tryLock(0, properties.leaseTime(), properties.timeUnit());

        }
    },
    FAIL_FAST() {
        @Override
        public boolean tryLock(RLock lock, MyLock properties) throws InterruptedException {

            boolean isLocked =  lock.tryLock(0, properties.leaseTime(), properties.timeUnit());
            if (!isLocked) {
                throw new RuntimeException("系统繁忙，请稍后再试");
            }
            return true;

        }
    },
    KEEP_TRYING() {
        @Override
        public boolean tryLock(RLock lock, MyLock properties) throws InterruptedException {

            lock.lock(properties.leaseTime(), properties.timeUnit());
            return true;

        }
    },
    SKIP_AFTER_RETRY_TIMEOUT() {
        @Override
        public boolean tryLock(RLock lock, MyLock properties) throws InterruptedException {

            return lock.tryLock(properties.waitTime(), properties.leaseTime(), properties.timeUnit());

        }
    },
    FAIL_AFTER_RETRY_TIMEOUT() {
        @Override
        public boolean tryLock(RLock lock, MyLock properties) throws InterruptedException {

            boolean isLocked =  lock.tryLock(properties.waitTime(), properties.leaseTime(), properties.timeUnit());
            if (!isLocked) {
                throw new RuntimeException("系统繁忙，请稍后再试");
            }
            return true;

        }
    },
    ;


    public abstract boolean tryLock(RLock lock, MyLock properties) throws InterruptedException;
}
