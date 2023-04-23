package com.rbsoft.appender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class App {

    static final Logger logger = LoggerFactory.getLogger("leakyconsole");

    public static void main(String[] args) throws InterruptedException {

        for (int i = 0; i < 100; i++) {
            String record = "RECORD_" + i;
            logger.info(record);
        }
    }
}
