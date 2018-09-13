package com.roboslyq.roboslyq.threadlocal;

public class ThreadLocalMain {

    public static void main(String[] args) throws InterruptedException {
        ThreadLocalService1 tls1 = new ThreadLocalService1();
        ThreadLocalService2 tls2 = new ThreadLocalService2();
        ThreadLocalService3 tls3 = new ThreadLocalService3();
        for(int i=0; i<1;i++){
            new Thread() {
                @Override
                public void run() {
                    tls1.add(2);
                    tls2.add(3);
                    tls3.add(4);
                    SourcesManager.tl1.remove();
                }
            }.start();
        }

        Thread.sleep(30000);
    }
}
