import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: Jaa
 * @Date: 2023/3/1 9:42
 * @Description:
 */
public class ABADemo {

    public static AtomicInteger a = new AtomicInteger(1);

    public static void main(String[] args) {
        Thread main = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("操作线程：" + Thread.currentThread().getName() + ", 初始值: " + a.get());
                try {
                    int expectNum = a.get();
                    int newNum = expectNum + 1;
                    Thread.sleep(1000); // 主线程休眠1秒，让出cpu

                    boolean isCASSuccess = a.compareAndSet(expectNum, newNum);
                    System.out.println("操作线程：" + Thread.currentThread().getName() + ", CAS操作: " + isCASSuccess);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "主线程");

        Thread other = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(20); // 确保Thread-main优先执行
                    a.incrementAndGet();
                    System.out.println("操作线程：" + Thread.currentThread().getName() + ", 【increment】，a = " + a.get());
                    a.decrementAndGet();
                    System.out.println("操作线程：" + Thread.currentThread().getName() + ", 【decrement】，a = " + a.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "干扰线程");

        main.start();
        other.start();
    }
}
