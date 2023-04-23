package com.rbsoft.appenderV2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    /*
    참고. Logger를 static하게 초기화하지 않으면, SubstitueLogger가 생성되면서 로깅이 무시되는 현상 발생한다.
    https://www.slf4j.org/codes.html#substituteLogger
     */
    public static Logger logger = LoggerFactory.getLogger("leakyconsoleV2");
    public static void main(String[] args) throws InterruptedException {
        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        long start = System.currentTimeMillis();
        IntStream.range(0, 10).forEach(i -> {
            threadPool.submit(() -> {
//                try {
//                    Thread.sleep(3000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

                System.out.println(logger.getName()+ " "+logger);
                logger.info("record_"+i);
            });
        });
        threadPool.shutdown();
        threadPool.awaitTermination(6, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();

        System.out.println(end - start);
    }

}
