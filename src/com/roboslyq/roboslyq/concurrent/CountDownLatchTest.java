package com.roboslyq.roboslyq.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class CountDownLatchTest {

    final static CountDownLatch countDownLatch = new CountDownLatch(4);
    static AtomicInteger integer = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {

        for (int i = 0; i <= 2; i++) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        System.out.println("事件准备开始-->threadId="+Thread.currentThread().getId());
                        int tmpInt = integer.getAndAdd(1);
                        Thread.sleep(tmpInt*1000);
                        System.out.println("事件准备耗时-->threadId="+Thread.currentThread().getId()+"-->"+tmpInt+"s,等待开始任务");

                        countDownLatch.countDown();
                        countDownLatch.await();
                        System.out.println("开始任务-->threadId="+Thread.currentThread().getId());


                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }
}
