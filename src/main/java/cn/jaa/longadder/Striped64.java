/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package cn.jaa.longadder;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongBinaryOperator;

/**
 * A package-local class holding common representation and mechanics
 * for classes supporting dynamic striping on 64bit values. The class
 * extends Number so that concrete subclasses must publicly do so.
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {
    /*
     * This class maintains a lazily-initialized table of atomically
     * updated variables, plus an extra "base" field. The table size
     * is a power of two. Indexing uses masked per-thread hash codes.
     * Nearly all declarations in this class are package-private,
     * accessed directly by subclasses.
     *
     * Table entries are of class Cell; a variant of AtomicLong padded
     * (via @Contended) to reduce cache contention. Padding is
     * overkill for most Atomics because they are usually irregularly
     * scattered in memory and thus don't interfere much with each
     * other. But Atomic objects residing in arrays will tend to be
     * placed adjacent to each other, and so will most often share
     * cache lines (with a huge negative performance impact) without
     * this precaution.
     *
     * In part because Cells are relatively large, we avoid creating
     * them until they are needed.  When there is no contention, all
     * updates are made to the base field.  Upon first contention (a
     * failed CAS on base update), the table is initialized to size 2.
     * The table size is doubled upon further contention until
     * reaching the nearest power of two greater than or equal to the
     * number of CPUS. Table slots remain empty (null) until they are
     * needed.
     *
     * A single spinlock ("cellsBusy") is used for initializing and
     * resizing the table, as well as populating slots with new Cells.
     * There is no need for a blocking lock; when the lock is not
     * available, threads try other slots (or the base).  During these
     * retries, there is increased contention and reduced locality,
     * which is still better than alternatives.
     *
     * The Thread probe fields maintained via ThreadLocalRandom serve
     * as per-thread hash codes. We let them remain uninitialized as
     * zero (if they come in this way) until they contend at slot
     * 0. They are then initialized to values that typically do not
     * often conflict with others.  Contention and/or table collisions
     * are indicated by failed CASes when performing an update
     * operation. Upon a collision, if the table size is less than
     * the capacity, it is doubled in size unless some other thread
     * holds the lock. If a hashed slot is empty, and lock is
     * available, a new Cell is created. Otherwise, if the slot
     * exists, a CAS is tried.  Retries proceed by "double hashing",
     * using a secondary hash (Marsaglia XorShift) to try to find a
     * free slot.
     *
     * The table size is capped because, when there are more threads
     * than CPUs, supposing that each thread were bound to a CPU,
     * there would exist a perfect hash function mapping threads to
     * slots that eliminates collisions. When we reach capacity, we
     * search for this mapping by randomly varying the hash codes of
     * colliding threads.  Because search is random, and collisions
     * only become known via CAS failures, convergence can be slow,
     * and because threads are typically not bound to CPUS forever,
     * may not occur at all. However, despite these limitations,
     * observed contention rates are typically low in these cases.
     *
     * It is possible for a Cell to become unused when threads that
     * once hashed to it terminate, as well as in the case where
     * doubling the table causes no thread to hash to it under
     * expanded mask.  We do not try to detect or remove such cells,
     * under the assumption that for long-running instances, observed
     * contention levels will recur, so the cells will eventually be
     * needed again; and for short-lived ones, it does not matter.
     */

    /**
     * Padded variant of AtomicLong supporting only raw accesses plus CAS.
     * <p>
     * JVM intrinsics note: It would be possible to use a release-only
     * form of CAS here, if it were provided.
     */
    @jdk.internal.vm.annotation.Contended
    static final class Cell {
        volatile long value;

        Cell(long x) {
            value = x;
        }

        final boolean cas(long cmp, long val) {
            return VALUE.compareAndSet(this, cmp, val);
        }

        final void reset() {
            VALUE.setVolatile(this, 0L);
        }

        final void reset(long identity) {
            VALUE.setVolatile(this, identity);
        }

        final long getAndSet(long val) {
            return (long) VALUE.getAndSet(this, val);
        }

        // VarHandle mechanics
        private static final VarHandle VALUE;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VALUE = l.findVarHandle(Cell.class, "value", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * Number of CPUS, to place bound on table size
     */
    // 表示当前计算机cpu数量.  控制 cells 数组长度的一个关键条件
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Table of cells. When non-null, size is a power of 2.
     */
    transient volatile Cell[] cells;

    /**
     * Base value, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     * 没有发生竞争时,数据会累加到base上 | 当cells扩容时,需要将数据写到base中
     */
    transient volatile long base;

    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     * 当cells扩容或者初始化时需要获取旋转锁, 0-表示无锁装填 1-表示有锁状态
     */
    transient volatile int cellsBusy;

    /**
     * Package-private default constructor.
     */
    Striped64() {
    }

    /**
     * CASes the base field.
     */
    final boolean casBase(long cmp, long val) {
        return BASE.compareAndSet(this, cmp, val);
    }

    final long getAndSetBase(long val) {
        return (long) BASE.getAndSet(this, val);
    }

    /**
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     * 通过CAS方式获取锁
     */
    final boolean casCellsBusy() {
        return CELLSBUSY.compareAndSet(this, 0, 1);
    }

    /**
     * Returns the probe value for the current thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     * 获取当前线程的hash值
     */
    static final int getProbe() {
        return (int) THREAD_PROBE.get(Thread.currentThread());
    }

    /**
     * Pseudo-randomly advances and records the given probe value for the
     * given thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     * 重置当前线程的hash值
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        THREAD_PROBE.set(Thread.currentThread(), probe);
        return probe;
    }

    /**
     * Handles cases of updates involving initialization, resizing,
     * creating new Cells, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     *
     * @param x              the value
     * @param fn             the update function, or null for add (this convention
     *                       avoids the need for an extra field or function in LongAdder).
     * @param wasUncontended false if CAS failed before call
     */
    // 都哪些情况会调用?
    // 1. true->说明 cells 未初始化,也就是多线程写base发生竞争了[重试|初始化cells]
    // 2. true-> 说明当前线程对应下标的cell为空,需要创建 longAccumulate 支持
    // 3. true->表示cas失败, 意味着 当前线程对应的cell有竞争

    // wasUncontended：只有cells初始化之后，并且当前线程 竞争修改失败，才会返回false
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {
        // h 表示线程的hash值
        int h;
        // 条件成立：说明当前线程 还未分配hash值
        if ((h = getProbe()) == 0) {
            // 给当前线程分配hash值
            ThreadLocalRandom.current(); // force initialization
            // 取出当前线程的hash值 赋值给h
            h = getProbe();
            // why? 因为默认情况下 当前线程 肯定时写入到了cells[0]位置.  不把它当作一次真正的竞争
            wasUncontended = true;
        }

        // 表示扩容意向, false 一定不会扩容  true 可能会扩容
        boolean collide = false;                // True if last slot nonempty
        // 自旋
        done:
        for (; ; ) {
            // cs 表示Cells引用
            // c 表示当前线程命中的cell
            // n 表示Cells数组长度
            // v 表示 期望值
            Cell[] cs;
            Cell c;
            int n;
            long v;
            // CASE1: 表示cells已经初始化过了, 当前线程应该将数据写入到对应的cell中
            if ((cs = cells) != null && (n = cs.length) > 0) {
                // CASE 1.1: true->表示当前线程对应下标位置的cell为null, 需要创建new Cell()
                if ((c = cs[(n - 1) & h]) == null) {

                    // true表示当前锁未被占用 false表示锁被占用
                    if (cellsBusy == 0) {       // Try to attach new Cell

                        // 拿当前的x创建Cell
                        Cell r = new Cell(x);   // Optimistically create
                        // 条件1: true表示当前锁未被占用 false表示锁被占用
                        // 条件2: true->表示当前线程获取锁 成功, false->表示当前线程获取锁失败
                        if (cellsBusy == 0 && casCellsBusy()) {
                            try {               // Recheck under lock
                                // rs 表示当前cells引用
                                Cell[] rs;
                                // m 表示cells数组的长度, j 表示当前线程命中的下标
                                int m, j;
                                // 条件1 条件2 恒成立
                                // rs[j = (m - 1) & h] == null 为了防止其他线程初始化过 该位置,当前线程再次初始化该位置 导致丢失数据
                                if ((rs = cells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    break done;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }
                    // 扩容意向 强制改为false, 不扩容
                    collide = false;
                }
                // CASE 1.2: wasUncontended：只有cells初始化之后，并且当前线程 竞争修改失败，才会返回false
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                    // CASE 1.3: 当前线程rehash过hash值,然后新命中的cell不为空
                    // true -> 写成功, 退出循环
                    // false -> 表示rehash之后命中的新的cell 也有竞争 重试1次   再重试1次
                else if (c.cas(v = c.value,
                        (fn == null) ? v + x : fn.applyAsLong(v, x)))
                    break;
                    // CASE 1.4:
                    // 条件1: n >= NCPU true -> 扩容意向 改为false, 表示不扩容了  false -> 说明cells数组还可扩容
                    // 条件2: cells != cs true -> 表示其他线程已经扩容过,当前线程rehash之后重试即可
                else if (n >= NCPU || cells != cs)
                    // 扩容意向 改为false, 表示不扩容了
                    collide = false;            // At max size or stale
                    // CASE 1.5:
                    // !collide == true 设置扩容意向为true, 但是不一定真的发生扩容
                else if (!collide)
                    collide = true;
                    // CASE 1.6: 真正扩容的逻辑
                    // 条件1: cellsBusy == 0 true->表示当前无锁状态,当前线程可以去竞争这把锁
                    // 条件2: casCellsBusy() true->表示当前线程获取锁 成功, 可以执行扩容逻辑  false->表示当前时刻有其他线程做扩容相关操作
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        // cells == cs 防止其他线成已经扩容,当前线程再次扩容
                        if (cells == cs)        // Expand table unless stale
                            cells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        // 释放锁
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                // 重置当前线程的hash值
                h = advanceProbe(h);
            }
            // CASE2: 前置条件cells还未初始化 cs为null
            // 条件一: true 表示当前未加锁
            // 条件二: cells == cs? 因为其他线程可能会在给cs赋值之后修改了 cells
            // 条件三: true 表示获取锁成功 会把cellsBusy设置成1, false表示其它线程正在持有这把锁
            else if (cellsBusy == 0 && cells == cs && casCellsBusy()) {
                try {                           // Initialize table
                    // cells == cs? 防止其他线程已经初始化了, 当前线程再次初始化, 防止丢失数据
                    if (cells == cs) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(x);
                        cells = rs;
                        break done;
                    }
                } finally {
                    cellsBusy = 0;
                }
            }
            // Fall back on using base
            // CASE3:
            // 1. 当前callBusy加锁状态,表示其他线程正在初始化cells,所以当前线程将值累加到base
            // 2. cells被其它线程初始化后,当前线程需要将数据累加到base
            else if (casBase(v = base,
                    (fn == null) ? v + x : fn.applyAsLong(v, x)))
                break done;
        }
    }

    private static long apply(DoubleBinaryOperator fn, long v, double x) {
        double d = Double.longBitsToDouble(v);
        d = (fn == null) ? d + x : fn.applyAsDouble(d, x);
        return Double.doubleToRawLongBits(d);
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn,
                                boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        done:
        for (; ; ) {
            Cell[] cs;
            Cell c;
            int n;
            long v;
            if ((cs = cells) != null && (n = cs.length) > 0) {
                if ((c = cs[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    break done;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                } else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (c.cas(v = c.value, apply(fn, v, x)))
                    break;
                else if (n >= NCPU || cells != cs)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == cs)        // Expand table unless stale
                            cells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            } else if (cellsBusy == 0 && cells == cs && casCellsBusy()) {
                try {                           // Initialize table
                    if (cells == cs) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        break done;
                    }
                } finally {
                    cellsBusy = 0;
                }
            }
            // Fall back on using base
            else if (casBase(v = base, apply(fn, v, x)))
                break done;
        }
    }

    // VarHandle mechanics
    private static final VarHandle BASE;
    private static final VarHandle CELLSBUSY;
    private static final VarHandle THREAD_PROBE;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            BASE = l.findVarHandle(Striped64.class,
                    "base", long.class);
            CELLSBUSY = l.findVarHandle(Striped64.class,
                    "cellsBusy", int.class);
            l = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<>() {
                        public MethodHandles.Lookup run() {
                            try {
                                return MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
                            } catch (ReflectiveOperationException e) {
                                throw new ExceptionInInitializerError(e);
                            }
                        }
                    });
            THREAD_PROBE = l.findVarHandle(Thread.class,
                    "threadLocalRandomProbe", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
