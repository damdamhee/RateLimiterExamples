package com.rbsoft.appender;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.IntStream;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DrainerTest {
    @Test
    void testDrain() throws Exception {
        int size = 10;
        BlockingQueue<LogEvent> queue = prepareAppendedQueue(size);
        Appender appender = Mockito.mock(Appender.class);
        Drainer drainer = new Drainer(queue, appender);

        int drained = drainer.drain(size);

        Assertions.assertEquals(size, drained);
        Assertions.assertEquals(0, queue.size());

    }

    @Test
    void testWaitToDrain() throws Exception {
        int size = 10;
        BlockingQueue<LogEvent> queue = prepareAppendedQueue(size);
        Appender appender = Mockito.mock(Appender.class);
        Drainer drainer = new Drainer(queue, appender);

        int drained = drainer.waitToDrain();

        Assertions.assertEquals(1, drained);
        Assertions.assertEquals(size-1, queue.size());
    }


    @DisplayName("큐에 포함된 원소 개수보다 많은 원소를 drain할 수 없다.")
    @Test
    void drainOverQueueSizeGeneratesException() {
        int size = 10;
        BlockingQueue<LogEvent> queue = prepareAppendedQueue(size);
        Appender appender = Mockito.mock(Appender.class);
        Drainer drainer = new Drainer(queue, appender);

        Assertions.assertThrows(AssertionError.class, () -> drainer.drain(size+1));

    }

    private BlockingQueue<LogEvent> prepareAppendedQueue(int size) {
        BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(size);
        IntStream.range(0, size).forEach(i -> {
            try {
                queue.put(new MutableLogEvent());
            } catch (InterruptedException e) {
                assert false;
            }
        });
        return queue;
    }
}
