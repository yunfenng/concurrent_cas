package cn.jaa.longadder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * @Author: Jaa
 * @Date: 2023/3/2 9:02
 * @Description: LongAdder VS AtomicLong elapse time
 */
public class LongAdderVSAtomicLongTest {

    public static void main(String[] args) {
        testLongAdderVSAtomicLong(1, 10000000);
        testLongAdderVSAtomicLong(10, 10000000);
        testLongAdderVSAtomicLong(20, 10000000);
        testLongAdderVSAtomicLong(40, 10000000);
        testLongAdderVSAtomicLong(80, 10000000);
    }

    /**
     * @param threadCount 开启线程数
     * @param times       累加次数
     */
    static void testLongAdderVSAtomicLong(final int threadCount, final int times) {
        try {
            System.out.println("threadCount: " + threadCount + ", times: " + times);
            long startTime = System.currentTimeMillis();
            testLongAdder(threadCount, times);
            System.out.println("LongAdder elapse: " + (System.currentTimeMillis() - startTime) + "ms");

            long startTime1 = System.currentTimeMillis();
            testAtomicLong(threadCount, times);
            System.out.println("AtomicLong elapse: " + (System.currentTimeMillis() - startTime1) + "ms");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void testAtomicLong(int threadCount, int times) throws InterruptedException {
        AtomicLong atomicLong = new AtomicLong();
        List<Thread> list = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            list.add(new Thread(() -> {
                for (int j = 0; j < times; j++) {
                    atomicLong.incrementAndGet();
                }
            }));
        }

        for (Thread thread : list) {
            thread.start();
        }

        for (Thread thread : list) {
            thread.join();
        }
    }

    private static void testLongAdder(int threadCount, int times) throws InterruptedException {
        LongAdder longAdder = new LongAdder();
        List<Thread> list = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            list.add(new Thread(() -> {
                for (int j = 0; j < times; j++) {
                    longAdder.increment();
                    // longAdder.add(1);
                }
            }));
        }
        for (Thread thread : list) {
            thread.start();
        }

        for (Thread thread : list) {
            thread.join();
        }
    }

}
