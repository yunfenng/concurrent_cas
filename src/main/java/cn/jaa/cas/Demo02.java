package cn.jaa.cas;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Jaa
 * @Date: 2023/2/28 8:56
 * @Description:
 */
public class Demo02 {

    // 总访问量
    volatile static int count = 0;

    /**
     * Q: 耗时太长的原因是什么？
     * A: 程序中的request方法使用了synchronized关键字修饰，保证并发的情况下，request方法同一时刻只允许
     * 一个线程进入，request加锁相当于串行执行了，count的结果与我们的与其一致，只是耗时太长了
     *
     * Q：如何解决耗时长的问题？
     * A: count++ 操作实际上有3步完成! (jvm执行引擎)
     *      1. 获取count的值,记录: A = count
     *      2. 将A值+1, 得到B: B = A + 1
     *      3. 将B值赋给count
     *      升级第3步的实现：
     *          1. 获取锁
     *          2. 获取count的最新的值，记作LV
     *          3. 判断LV是否等于A，若相等，则将B的值赋给count，返回true，否则返回false
     *          4. 释放锁
     *
     */
    // 模拟访问的方法
    public /*synchronized*/ static void request() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(5);
        /**
         * Q: 问题出在哪里?
         * A: count++ 操作实际上有3步完成! (jvm执行引擎)
         *    1. 获取count的值,记录: A = count
         *    2. 将A值+1, 得到B: B = A + 1
         *    3. 将B值赋给count
         *
         *    如果有A, B两个线程同时执行了count++, 通过执行上面步骤的第一步, 得到的
         *    count是一样的, 3步执行结束后, count只加1, 导致count结果不正确!
         * Q: 如何解决这个问题?
         * A: 对count++进行操作的时候, 让多个线程进行排队处理, 多个线程同时到达request()方法的时候,
         * 只允许一个线程进行操作,其他的线程在外面等着, 等里面的处理完毕出来之后, 外面等着的再进去一个,
         * 这样操作的count++就是排队进行的, 结果一定正确.
         *
         * Q: 如何实现排队效果?
         * A: java中synchronized关键字和ReentrantLock都可以实现对资源加锁, 保证并发正确性
         * 多线程的情况下,可以保证被锁定的资源被“串行”访问
         */
        // count++;
        int expectCount; // 期望值
        while (!compareAndSwap((expectCount = getCount()), expectCount + 1)) {

        }
    }

    /**
     *
     * @param expectCount 期望值count
     * @param newCount    需要给count赋值的新值
     * @return            成功返回 true，失败返回 false
     */
    public static synchronized boolean compareAndSwap(int expectCount, int newCount) {
        // 判断count当前值是否和期望值expectCount一致, 如果一致, 将newCount赋值给count
        if (getCount() == expectCount) {
            count = newCount;
            return true;
        }
        return false;
    }

    public static int getCount() {
        return count;
    }

    public static void main(String[] args) throws InterruptedException {
        // 开始时间
        long startTime = System.currentTimeMillis();
        int threadSize = 100;
        CountDownLatch countDownLatch = new CountDownLatch(threadSize);

        for (int i = 0; i < threadSize; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    // 模拟用户行为,每个用户访问10次
                    try {
                        for (int j = 0; j < 10; j++) {
                            request();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
            thread.start();
        }
        // 如何保证100个线程 执行结束后, 再执行后面的代码?
        countDownLatch.await();
        long endTime = System.currentTimeMillis();

        System.out.println(Thread.currentThread().getName() + ", 耗时: " + (endTime - startTime) + ", count = " + count);
    }
}
