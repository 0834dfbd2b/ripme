package com.rarchives.ripme.ripper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.rarchives.ripme.utils.Utils;

public class DownloadThreadPool {

    private static final Logger logger = Logger.getLogger(DownloadThreadPool.class);
    private ExecutorService threadPool = null;

    public DownloadThreadPool() {
        int threads = Utils.getConfigInteger("threads.size", 10);
        logger.debug("Initializing thread pool with " + threads + " threads");
        threadPool = Executors.newFixedThreadPool(threads);
    }

    public void addThread(Thread t) {
        threadPool.execute(t);
    }
    
    public void waitForThreads() {
        logger.info("[ ] Waiting for threads to finish...");
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for threads to finish: ", e);
        }
    }
}
