package com.rbsoft.appenderV2;

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.status.StatusLogger;

/*
 * Leaky Bucket
 * 입력은 급증할 수 있으나 출력은 일정하게 유지한다.
 * [단점] 버퍼가 꽉차면 입력이 제때 처리되지 못하는 Starvation 이슈가 발생할 수 있다
 * [단점] 요청이 주어진 시간 내에 완료된다는 보장이 없다
 * 변수 - queue, 처리율(ex. 처리개수/분)
 */

/*
[클라이언트 요청 방식 2]
1. 사용자 요청 발생 (ReqA)
2. ReqA 큐잉
    2.1 Queue is Full : 요청은 특정 시간 대기 > 재기도 or 버려진다
    2.2 Queue is not Full : 요청은 Queue에 추가된다
3.
4. 동시성 이슈는 없는가?
 */
@Plugin(
    name = "LeakyBucketAppenderV2",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE
)
public class LeakyBucketAppenderV2 extends AbstractAppender {
    private static final Logger LOGGER = StatusLogger.getLogger();

    private final String name;
    private final RateLimiter rateLimiter;
    private final int maxRetry;
    private final Appender appender;

    @VisibleForTesting
    public LeakyBucketAppenderV2(String name, RateLimiter rateLimiter, int maxRetry, Appender appender) {
        super(name, null, null, true, Property.EMPTY_ARRAY);
        this.name = name;
        this.rateLimiter = rateLimiter;
        this.maxRetry = maxRetry;
        this.appender = appender;
    }


    @PluginFactory
    public static LeakyBucketAppenderV2 create(
        @PluginAttribute("name") String name,
        @PluginAttribute("timeoutSec") long timeoutSec,
        @PluginAttribute("permits") int permits,
        @PluginAttribute("maxRetry") int maxRetry,
        @PluginElement("appender") Appender appender) {

        RateLimiter rateLimiter = new RateLimiter(TimeUnit.SECONDS.toNanos(timeoutSec), permits);
        return new LeakyBucketAppenderV2(name, rateLimiter, maxRetry, appender);
    }

    @Override
    public void append(LogEvent record) {
        int retried = 0;
        while(retried < this.maxRetry) {
            try {
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("is interrupted"); //TODO -msg
                if(this.rateLimiter.acquire()) {
                    this.appender.append(record.toImmutable());
                    break;
                }
            } catch (Exception e) {
                retried++;
            }
        }
        if(this.maxRetry <= retried) {
            LOGGER.error(String.format("failed to append log within max retries. retried %d times.", retried));
        }
    }
}



