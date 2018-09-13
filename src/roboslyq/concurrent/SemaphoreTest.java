package roboslyq.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class SemaphoreTest {
    public static void main(String[] args) {
       final Count count = new Count();
        CountDownLatch countDownLatch = new CountDownLatch(10000);
       count.setCount(0);
        Semaphore semaphore = new Semaphore(1);

        for(int j=0;j<10000;j++){
            new Thread(){
                @Override
                public void run(){
                    try {
                        semaphore.acquire();
                            int tmp = count.getCount()+1;
                            count.setCount(tmp);
                            countDownLatch.countDown();
                        semaphore.release();

                    } catch (InterruptedException e) {
//                        e.printStackTrace();
                    }
                }
            }.start();
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(count.getCount());
    }
}

class  Count{
    private int count;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}