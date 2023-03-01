import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * @Author: Jaa
 * @Date: 2023/3/1 9:42
 * @Description: 使用AtomicStampedReference解决ABA问题
 */
public class ABADemo02 {

    public static AtomicStampedReference<Integer> a = new AtomicStampedReference(new Integer(1), 1);

    public static void main(String[] args) {
        Thread main = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("操作线程：" + Thread.currentThread().getName() + ", 初始值: " + a.getReference());
                try {
                    Integer expectReference = a.getReference();
                    Integer newReference = expectReference + 1;
                    Integer expectStamp = a.getStamp();
                    Integer newStamp = expectStamp + 1;
                    Thread.sleep(1000); // 主线程休眠1秒，让出cpu

                    boolean isCASSuccess = a.compareAndSet(expectReference, newReference, expectStamp, newStamp);
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
                    a.compareAndSet(a.getReference(), (a.getReference() + 1), a.getStamp(), (a.getStamp() + 1)); // a+1, a=2
                    System.out.println("操作线程：" + Thread.currentThread().getName() + ", 【increment】，a = " + a.getReference());
                    a.compareAndSet(a.getReference(), (a.getReference() - 1), a.getStamp(), (a.getStamp() + 1)); // a-1, a=1
                    System.out.println("操作线程：" + Thread.currentThread().getName() + ", 【decrement】，a = " + a.getReference());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "干扰线程");

        main.start();
        other.start();
    }
}
