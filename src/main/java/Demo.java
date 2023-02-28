import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Jaa
 * @Date: 2023/2/28 8:56
 * @Description:
 */
public class Demo {

    // 总访问量
    static int count = 0;

    // 模拟访问的方法
    public static void request() throws InterruptedException {
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
        count++;
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
                    for (int j = 0; j < 10; j++) {
                        try {
                            request();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            countDownLatch.countDown();
                        }
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
