package com.tianji.promotion.utils;


import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
@Aspect
@RequiredArgsConstructor
public class MyLockAspect implements Ordered {

    private final MyLockFactory myLockFactory;

    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {

        RLock lock = myLockFactory.getLock(myLock.lockType(), myLock.name());

        boolean isLocked = myLock.lockStrategy().tryLock(lock, myLock);

        if (!isLocked) {
            return null;
        }
        try {
            return pjp.proceed();
        } finally {
            lock.unlock();
        }

    }

    @Override
    public int getOrder() {
        return 0;
    }
}
