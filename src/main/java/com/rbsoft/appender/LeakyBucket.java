package com.rbsoft.appender;

import java.util.concurrent.BlockingQueue;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.status.StatusLogger;

class LeakyBucket extends Thread {
    private static final Logger LOGGER = StatusLogger.getLogger();

    public static class DefaultLeaker implements Runnable {
        private final BlockingQueue<LogEvent> queue;
        private final int BATCH_SIZE;
        private final long TIME_SLICE;
        private int numProcessed;
        private long timeSliceLeft;
        private final Drainer drainer;
        private boolean stopped = false;

        public static DefaultLeaker newLeaker(BlockingQueue<LogEvent> queue, long durationMillis,
            int batchSize, Appender appender) {
            Drainer drainer = new Drainer(queue, appender);
            return new DefaultLeaker(queue, durationMillis, batchSize, drainer);
        }

        public DefaultLeaker(BlockingQueue<LogEvent> queue, long durationMillis, int batchSize, Drainer drainer) {
            this.queue = queue;
            this.BATCH_SIZE = batchSize;
            this.TIME_SLICE = durationMillis;
            this.timeSliceLeft = durationMillis;
            this.numProcessed = 0;
            this.drainer = drainer;
        }

        //TODO - test. numProcessed < MAX_RECORDS_BATCH일 때, drain이 제대로 되는지
        //TODO - test. numProcessed > MAX_RECORDS_BATCH일 때, 배치가 초기화되는지
        @Override
        public void run() {
            while(!stopped) {
                try {
                    stopIfInterrupted();

                    if(this.numProcessed < BATCH_SIZE) {
                        long start = System.currentTimeMillis();

                        int numRecordsToDrain = Math.min(BATCH_SIZE - this.numProcessed, this.queue.size());

                        if(numRecordsToDrain <= 0) this.numProcessed += this.drainer.waitToDrain(); //1 record
                        else this.numProcessed += this.drainer.drain(numRecordsToDrain); //n records

                        long end = System.currentTimeMillis();

                        LOGGER.debug(String.format("records processed(%d)", this.numProcessed));
                        this.timeSliceLeft = this.timeSliceLeft - (end-start);
                        if(this.timeSliceLeft <= 0) this.initiateBatch();
                    } else {
                        LOGGER.debug(String.format("records processed(%d) reached max batch size(%d). Sleeping for %d millis", this.numProcessed,
                            BATCH_SIZE, this.timeSliceLeft));
                        Thread.sleep(this.timeSliceLeft);
                        this.initiateBatch();
                    }
                } catch (InterruptedException e) {
                    stopIfInterrupted();
                }
            }
        }

        private void stopIfInterrupted() {
            try {
                if(Thread.currentThread().isInterrupted()) {
                    this.stopped = true;
                    this.drainer.drainRemaining();
                }
            } catch (InterruptedException e) {
                //do nothing
            } finally {
                LOGGER.error(String.format("thread(%s) is interrupted. terminating...(%d unsent logs)", Thread.currentThread().getName(), this.queue.size()));
            }
        }

        /*
        비동기 전송을 Logger 레벨에서 적용하면?
            TODO - logger.info() -> msg -> queue -> appender -> queue -> leaker (Log4j 아키텍쳐 확인해보기)
            TODO - 애초에 async appender를 추가로 적용할 필요가 있을까?
        비동기 전송을 직접 구현하면?
            * Raven에서 적용할 수 있는지
            * Raven에서 적용할 수 없다면, appender.append()작업을 future로 구현할 필요가 있다.
        */
        private void initiateBatch() {
            LOGGER.debug(String.format("set timeSlice=%d, numProcessed=0", TIME_SLICE));
            this.timeSliceLeft = TIME_SLICE;
            this.numProcessed = 0;
        }
    }

    public static LeakyBucket nonDaemon(String name, Runnable runnable) {
        return new LeakyBucket(name, runnable, false);
    }

    private LeakyBucket(String name, Runnable target, boolean daemon) {
        super(target);
        this.setName(name);
        this.setDaemon(daemon);
    }
}
