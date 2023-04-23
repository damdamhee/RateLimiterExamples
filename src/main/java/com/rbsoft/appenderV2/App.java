package com.rbsoft.appenderV2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        long start = System.currentTimeMillis();
        IntStream.range(0, 10).forEach(i -> {
            threadPool.submit(() -> {
                Logger logger = LoggerFactory.getLogger("leakyconsoleV2");
                logger.info("record_"+i);
                while(true){}
            });
        });
//        threadPool.shutdown();
//        threadPool.awaitTermination(6, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();

        System.out.println(end - start);
    }

}
