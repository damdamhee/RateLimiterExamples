package com.rbsoft.appender;

import com.google.common.annotations.VisibleForTesting;
import com.rbsoft.appender.LeakyBucket.DefaultLeaker;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
    name = "LeakyBucketAppender",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE
)
public class LeakyBucketAppender extends AbstractAppender {

    //TODO - AbstractManager를 상속받는 것을 하나 만들까?
    //TODO - Manager가 왜 필요할까?
    private static final Logger LOGGER = StatusLogger.getLogger();

    private final String name;
    private final long TIMEOUT;
    private final BlockingQueue<LogEvent> queue;
    private final long MAX_RETRY;
    private final Thread leaker;


    //동기화 대상. 여러 Task에서 접근하면 이슈가 발생할 수 있다.
    //하나의 TaskManager에서는 여러개의 Task가 뜰 수 있는데
    //본 로거는 TaskManager에 적용되는 로거인가? 그렇다.


    //방법1. 매 1초마다 Timer마다 최대 RECORDS_PER_SEC만큼 drain하는 방식 (thread sleep 1)
    //매초마다?매 배치마다? 전송 가능 최대 레코드 수 제한 가능.
    //로그 전송에 오래 걸리면? 설정한 전송률이 나오지 않을 것이다.

    //방법2. 전송에 사용된 시간(end-start)를 누적하다가 1초가 넘으면 ('사용된 시간'=0, 처리한 레코드=0)으로 초기화.
    //timeSlice(1초) 아직 남음 && 전송 가능 개수 남음 ==> 레코드 전송, timeSlice 및 전송수 increment
    //timeSlice(1초) 아직 남음 && 전송 가능 개수를 모두 소비함 ==> 남은 시간만큼 sleep
    //timeSlice(1초) 모두 사용함 ==> timeSlice초기화(=0), 레코드 전송
    //로그 전송에 오래 걸리면? 약속한 전송률이 안나온다. 애초에 전송률을 보장하는게 아니라 TimeUnit당 전송 가능 최대 횟수를 보장하도록 하면?

    //방법1 or 방법2 중에 어떤것을 선택할지는 전송률 보장 정도에 따라 다른 것 같다.
    //방법1 - 전송 지연에 따라 1초마다의 전송률이 아닐 수도 있다.
    //방법2 - 전송 지연에 따라 1초보다 오래 소요될 수 있다.

    //Timeslice Based

    /**
     * Leak의 책임
        * TIMESLICE 기반 배치 처리
     */


    @VisibleForTesting
    public LeakyBucketAppender(String name, BlockingQueue<LogEvent> queue, long timeoutMillis, long maxRetry, Thread leaker) {
        super(name, null, null, true, Property.EMPTY_ARRAY);
        this.name = name;
        this.queue = queue;
        this.TIMEOUT = timeoutMillis;
        this.MAX_RETRY = maxRetry;
        this.leaker = leaker;
    }


    @PluginFactory
    public static LeakyBucketAppender create(
        @PluginAttribute("name") String name,
        @PluginAttribute("bucketSize") int bucketSize,
        @PluginAttribute("timeoutMillis") long timeout,
        @PluginAttribute("maxRetry") long maxRetry,
        @PluginAttribute("durationMillis") long durationMillis,
        @PluginAttribute("batchSize") int batchSize,
        @PluginElement("appender") Appender appender) {
         if(bucketSize < batchSize) throw new IllegalArgumentException("버킷의 크기는 최소한 초당처리레코드 수보다 커야 합니다.");
         if(bucketSize < batchSize) throw new IllegalArgumentException("bucket size should be at least of batch size");

        BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(bucketSize, true);
        DefaultLeaker leaker = DefaultLeaker.newLeaker(queue, durationMillis, batchSize,
            appender);
        LeakyBucket leakyBucket = LeakyBucket.nonDaemon("LeakyBucket", leaker);
        return new LeakyBucketAppender(name, queue, timeout, maxRetry, leakyBucket);
    }



    /**
     * Queue에 레코드 추가 시도를 한다.
     * 레코드 추가 시 최대 TIMEOUT만큼 대기.
     * 레코드 추가 시 최대 MAX_RETRY 회수만큼 재시도를 수행.
     * @param record
     */
    //TODO - test. 큐 여유 상황에서 최대 개수만큼 append하는지
    //TODO - test. 큐 여유x 상황에서 최대 N회만큼 재시도하는지 여부
    @Override
    public void append(LogEvent record) {
        int retried = 0;
        while(retried < MAX_RETRY) {
            try {
                if(this.queue.offer(record.toImmutable(), TIMEOUT, TimeUnit.MILLISECONDS)) break;
                retried++;
            } catch (InterruptedException e) {
                this.leaker.interrupt();
                throw new IllegalStateException("leakybucket thread is interrupted. no existing sender");
            }
        }
        if(MAX_RETRY <= retried) {
            LOGGER.error(String.format("failed to append log within max retries. retried %d times.", retried));
        }
    }

    @Override
    public void start() {
        this.leaker.start();
        super.start();
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        this.leaker.interrupt();
        return super.stop(timeout, timeUnit);
    }

}



