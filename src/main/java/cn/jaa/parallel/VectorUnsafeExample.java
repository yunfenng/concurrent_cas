package cn.jaa.parallel;

import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author: Jaa
 * @Date: 2023/7/9 16:16
 */
public class VectorUnsafeExample {

    // 在 Java 语言中，大部分的线程安全类都属于这种类型，例如 Vector、HashTable、Collections 的 synchronizedCollection() 方法包装的集合等
    // 不加 synchronized 会抛异常
    // Exception in thread "main" java.lang.UnsupportedOperationException
    //     at java.util.Collections$UnmodifiableMap.put(Collections.java:1457)
    //     at ImmutableExample.main(ImmutableExample.java:9)
    private static Vector<Integer> vector = new Vector<>();

    public static void main(String[] args) {
        while (true) {
            for (int i = 0; i < 100; i++) {
                vector.add(i);
            }
            ExecutorService executorService = Executors.newCachedThreadPool();
            executorService.execute(() -> {
                synchronized (vector) {
                    for (int i = 0; i < vector.size(); i++) {
                        vector.remove(i);
                    }
                }
            });

            executorService.execute(() -> {
                synchronized (vector) {
                    for (int i = 0; i < vector.size(); i++) {
                        vector.get(i);
                    }
                }
            });

            executorService.shutdown();
        }
    }
}
