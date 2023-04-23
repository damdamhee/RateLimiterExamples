package com.rbsoft.appender;


import com.rbsoft.appender.LeakyBucket.DefaultLeaker;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.IntStream;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LeakyBucketAppenderV2Test {
    @DisplayName("큐가 여유로운 상횡에서의 append 요청은 요청횟수 만큼 큐에 요청이 추가된다.")
    @Test
    void queueSizeEqualsNumberOfAppended() {
        int bucketSize = 10;
        BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(bucketSize);
        LeakyBucketAppender appender = new LeakyBucketAppender("test-appender", queue, 0, 3, mockLeakyBucket());

        IntStream.range(0, bucketSize).forEach(i -> {
            appender.append(new MutableLogEvent());
        });

        Assertions.assertEquals(bucketSize, queue.size());
    }

    @DisplayName("큐가 여유롭지 않은 상황에서의 append요청은 retry를 발생시킨다.")
    @Test
    void verifyRetry() {
        int bucketSize = 10;
        int overLimitRequest = 3;
        int maxRetry = 3;
        BlockingQueue<LogEvent> queue = Mockito.spy(new ArrayBlockingQueue<>(bucketSize));
        LeakyBucketAppender appender = new LeakyBucketAppender("test-appender", queue, 0, maxRetry, mockLeakyBucket());

        IntStream.range(0, bucketSize+overLimitRequest).forEach(i -> {
            appender.append(new MutableLogEvent());
        });

        Assertions.assertDoesNotThrow(() -> {
            Mockito.verify(queue, Mockito.times(bucketSize + overLimitRequest * maxRetry))
                .offer(Mockito.any(), Mockito.anyLong(), Mockito.any());
        });
    }

    private LeakyBucket mockLeakyBucket() {
        DefaultLeaker leaker = Mockito.mock(DefaultLeaker.class);
        Mockito.doNothing().when(leaker).run();
        return LeakyBucket.nonDaemon("leakybucket", leaker);
    }
}
