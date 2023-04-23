package com.rbsoft.appender;

import java.util.concurrent.BlockingQueue;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * Drainer의 책임 - Queue로부터 데이터를 읽어서 전송하는 전송자 역할
    * queue consume > appender를 통한 전송
    * 별도로 정의한 이유. 데이터 읽기 및 출력의 역할 테스트 목적. Leaker에 있으면 private으로 모두 구현됨.
 */
class Drainer {
    private static final Logger LOGGER = StatusLogger.getLogger();

    private final BlockingQueue<LogEvent> queue;
    private final Appender appender;

    public Drainer(BlockingQueue<LogEvent> queue, Appender appender) {
        this.queue = queue;
        this.appender = appender;
    }

    //TODO - test. numRecordsToDrain 만큼 drain하는지
    public int drain(final int numRecordsToDrain) throws InterruptedException {
        assert numRecordsToDrain <= this.queue.size();

        int drained = 0;
        while(drained < numRecordsToDrain) {
            //TODO - Q. LogEvent가 Mutable한가..? yes...
            LogEvent record = this.queue.take(); //blocking
            appender.append(record); //sync
            drained++;
        }
        LOGGER.debug(String.format("drained %d records", drained));
        return drained;
    }

    //TODO - test. 큐에 있는 전체 원소만큼 drain하는지
    public int drainRemaining() throws InterruptedException {
        return this.drain(this.queue.size());
    }


    //TODO - test. 항상 1개만 리턴하는지
    public int waitToDrain() throws InterruptedException {
        LogEvent record = queue.take();
        appender.append(record);
        LOGGER.debug(String.format("drained %d records", 1));
        return 1;
    }
}
