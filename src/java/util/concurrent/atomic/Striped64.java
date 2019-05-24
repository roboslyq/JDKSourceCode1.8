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

package java.util.concurrent.atomic;
import java.util.function.LongBinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A package-local class holding common representation and mechanics
 * for classes supporting dynamic striping on 64bit values. The class
 * extends Number so that concrete subclasses must publicly do so.
 *
 * Striped64的设计核心思路就是通过内部的分散计算来避免竞争。
 * Striped64内部包含一个base和一个Cell[] cells数组，又叫hash表。
 * 没有竞争的情况下，要累加的数通过cas累加到base上；
 * 如果有竞争的话，会将要累加的数累加到Cells数组中的某个cell元素里面。
 * 所以整个Striped64的值为sum=base+∑[0~n]cells。
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
     * (via @sun.misc.Contended) to reduce cache contention. Padding
     * is overkill for most Atomics because they are usually
     * irregularly scattered in memory and thus don't interfere much
     * with each other. But Atomic objects residing in arrays will
     * tend to be placed adjacent to each other, and so will most
     * often share cache lines (with a huge negative performance
     * impact) without this precaution.
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
     *
     * JVM intrinsics note: It would be possible to use a release-only
     * form of CAS here, if it were provided.
     * 为提高性能，使用注解@sun.misc.Contended，用来避免伪共享，
     *
     */
    @sun.misc.Contended
    static final class Cell {
        //用来保存要累加的值
        volatile long value;

        Cell(long x) { value = x; }

        //使用UNSAFE类的cas来更新value值(此方法非常关键，与AutomicLong基本一模一样)
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;

        //value在Cell类中存储位置的偏移量；
        private static final long valueOffset;

        //这个静态方法用于获取偏移量
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> ak = Cell.class;
                valueOffset = UNSAFE.objectFieldOffset
                    (ak.getDeclaredField("value"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /** Number of CPUS, to place bound on table size */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Table of cells. When non-null, size is a power of 2.
     * 存放Cell的hash表，大小为2的幂。
     * 此变量是LongAdder高性能实现的必杀器：
     * AtomicInteger只有一个value，所有线程累加都要通过cas竞争value这一个变量，高并发下线程争用非常严重；
     * 而LongAdder则有两个值用于累加，一个是base，它的作用类似于AtomicInteger里面的value，在没有竞争的情况不会用到cells数组，
     * 它为null，这时使用base做累加，有了竞争后cells数组就上场了，第一次初始化长度为2，
     * 以后每次扩容都是变为原来的两倍，直到cells数组的长度大于等于当前服务器cpu的数量为止就不在扩容
     * （想下为什么到超过cpu数量的时候就不再扩容）；每个线程会通过线程对cells[threadLocalRandomProbe%cells.length]
     * 位置的Cell对象中的value做累加，这样相当于将线程绑定到了cells中的某个cell对象上；
     * 大大减小了竞争压力。
     */
    transient volatile Cell[] cells;

    /**
     * Base value, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     *  基础值，
     *  1. 在开始没有竞争的情况下，将累加值累加到base
     *  2. 在cells初始化的过程中，cells处于不可用的状态，这时候也会尝试将通过cas操作值累加到base。
     */
    transient volatile long base;

    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     * 自旋锁，通过CAS操作加锁，用于保护创建或者扩展Cell表。
     * cellsBusy，它有两个值0 或1，它的作用是当要修改cells数组时加锁，防止多线程同时修改cells数组，0为无锁，1为加锁，加锁的状况有三种
     * 1. cells数组初始化的时候；
     * 2. cells数组扩容的时候；
     * 3. 如果cells数组中某个元素为null，给这个位置创建新的Cell对象的时候；
     */
    transient volatile int cellsBusy;

    /**
     * Package-private default constructor
     */
    Striped64() {
    }

    /**
     * CASes the base field.
     * 使用UNSAFE完成基本的CAS操作
     *  casBase方法很简单，就是通过UNSAFE类的cas设置成员变量base的值为base+要累加的值
     *  casBase执行成功的前提是无竞争，这时候cells数组还没有用到为null，可见在无竞争的情况下是类似于AtomticInteger处理方式，
     *  使用cas做累加。
     */
    final boolean casBase(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, BASE, cmp, val);
    }

    /**
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     * 原子性操作，将cellsBusy从空闲调整为繁忙。相当于获取锁操作。
     */
    final boolean casCellsBusy() {
        return UNSAFE.compareAndSwapInt(this, CELLSBUSY, 0, 1);
    }

    /**
     * Returns the probe value for the current thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     * 获取线程Thread的threadLocalRandomProbe变量值，返回一个Int。不同线程值不一样。
     * (由于打包限制，所以从ThreadLocalRandom中复制一份代码,此代码在ThreadLocalRandom中同样可以找到)
     */
    static final int getProbe() {
        return UNSAFE.getInt(Thread.currentThread(), PROBE);
    }

    /**
     * Pseudo-randomly advances and records the given probe value for the
     * given thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        UNSAFE.putInt(Thread.currentThread(), PROBE, probe);
        return probe;
    }

    /**
     * Handles cases of updates involving initialization, resizing,
     * creating new Cells, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     * "Accumulate"在计算机科学中意指"累加"。
     *
     *  本方法主要涉及在竞争情况下的CELL初始化，大小调整及新CELL的创建。
     *  此方法的出现的主要背景是：在竞争条件下，乐观锁自旋重试的非模块化的代码，我们通过重新检查CELL集合来解决此问题。
     *
     * @param x the value 原始Long的值
     * @param fn the update function, or null for add (this convention
     * avoids the need for an extra field or function in LongAdder).
     *           更新函数，如果是调用add()方法,那么为null。因为add()方法不需要额外的功能。
     * @param wasUncontended false if CAS failed before call
     *                       表示 cas 是否失败，如果是失败则为false,表示多线程并发存在竞争。
     */
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {
        //获取当前线程的threadLocalRandomProbe值作为hash值,如果当前线程的threadLocalRandomProbe为0，表示还没有进行初始化。
        //  说明当前线程是第一次进入该方法，则强制设置线程的threadLocalRandomProbe为ThreadLocalRandom类的成员静态私有变量probeGenerator的值，
        //   后面会详细将hash值的生成;

        //另外需要注意，如果threadLocalRandomProbe=0，代表新的线程开始参与cell争用的情况
        //  1.当前线程之前还没有参与过cells争用（也许cells数组还没初始化，进到当前方法来就是为了初始化cells数组后争用的）
        //      ,是第一次执行base的cas累加操作失败；
        //  2.或者是在执行add方法时，对cells某个位置的Cell的cas操作第一次失败，则将wasUncontended设置为false，
        //          那么这里会将其重新置为true；第一次执行操作失败；

        //  凡是参与了cell争用操作的线程threadLocalRandomProbe都不为0；
        int h;
        if ((h = getProbe()) == 0) {

            //初始化ThreadLocalRandom;初始化后：0x9e3779b9
            ThreadLocalRandom.current(); // force initialization

            //将h设置为PROBE，经过初始化后值为0x9e3779b9
            h = getProbe();

            //设置未竞争标记为true
            wasUncontended = true;
        }

        //cas冲突标志，表示当前线程hash到的Cells数组的位置，做cas累加操作时与其它线程发生了冲突，cas失败；
        // collide=true代表有冲突，collide=false代表无冲突
        boolean collide = false;                // True if last slot nonempty

        /*
         * 1、整个 for(;;) 死循环，都是以 cas 操作成功而结束。否则则会修改cellsBusy ，wasUncontended 及collide 相关标记位，重新进入循环。
         *
         * 2、以下循环原则
         *     为并发环境下要考虑各种操作的原子性，所以对于锁都进行了 double check。
         *     操作都是逐步升级，以最小的代价实现功能。
         *
         * 3、具体整个循环包括如下三个分支情况：
         *
         *   (1)cells 不为空(cells数组已经正常初始化了的情况（这个if分支处理add方法的四个条件中的3和4）)
         *       (a)如果 cell[i] 某个下标为空，则 new 一个 cell，并初始化值，然后退出
         *       (b)如果 cas 失败，继续循环
         *       (c)如果 cell 不为空，且 cell cas 成功，退出
         *       (d)如果 cell 的数量，大于等于 cpu 数量或者已经扩容了，继续重试。（扩容没意义）
         *       (e)设置 collide 为 true。
         *       (f)获取 cellsBusy 成功就对 cell 进行扩容，获取 cellBusy 失败则重新 hash 再重试。
         *   (2)处理cells数组没有初始化或者长度为0的情况；（这个分支处理add方法的四个条件中的1和2）
         *           cells 为空且获取到 cellsBusy ，init cells 数组，然后赋值退出。
         *   (3)处理如果cell数组没有初始化，并且其它线程正在执行对cells数组初始化的操作，及cellbusy=1；
         *           则尝试将累加值通过cas累加到base上。
         *           cellsBusy 获取失败，则进行 baseCas ，操作成功退出，不成功则重试。
         */
        for (;;) {
            Cell[] as;
            Cell a;
            int n;
            long v;
            //=================主分支一========================

            // 此时cells已经被其它线程初始化,则直接使用cells中相关cell完成具体业务操作。

            if ((as = cells) != null && (n = as.length) > 0) {
                /**
                 *内部小分支一，判断当前线程在cells中对应的位置Cell是否存在，如果存就在已经存在的Cell上CAS竞争，如果不存在则新建：
                 *      这个是处理add方法内部if分支的条件 3：如果被hash到的位置为null，
                 *      说明没有线程在这个位置设置过值，没有竞争，可以直接使用，则用x值作为初始值创建一个新的Cell对象，
                 *      对cells数组使用cellsBusy加锁，然后将这个Cell对象放到cells[m%cells.length]位置上
                 */
                // 当前线程在cells中对应的Cell为null,表示需要创建新Cell，然后赋值到cells中对应的位置。若创建成功，则表示完成操作，返回break返回。
                //as[(n - 1) & h]操作相当于对h取模，只不过比起取摸，因为是 与 的运算所以效率更高。
                if ((a = as[(n - 1) & h]) == null) {
                    //cellsBusy == 0 代表当前没有线程cells数组做修改，相当于锁的效果(锁整个cells)
                    if (cellsBusy == 0) {       // Try to attach new Cell

                        //将要累加的x值作为初始值创建一个新的Cell对象，
                        Cell r = new Cell(x);   // Optimistically create

                        //如果cellsBusy=0无锁，则通过cas将cellsBusy设置为1加锁
                        if (cellsBusy == 0 && casCellsBusy()) { // CAS获取锁操作

                            //标记Cell是否创建成功并放入到cells数组被hash的位置上
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;

                                //再次检查cells数组不为null，且长度不为空，且hash到的位置的Cell为null
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {

                                    //存入Cell到cells指定的位置中。
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                //去掉锁
                                cellsBusy = 0;
                            }
                            //生成成功，跳出循环，结束整个流程。
                            if (created)
                                break;
                            //如果created为false，说明上面指定的cells数组的位置cells[m%cells.length]已经有其它线程设置了cell了，
                            // 继续执行循环，重新竞争更新Cell值。继续执行时，不会进入此分之，因为cells[m%cells.length]不为空了。
                            continue;           // Slot is now non-empty
                        }
                    }
                    //如果执行的当前行，代表cellsBusy=1，有线程正在更改cells数组，代表产生了冲突，将collide设置为false
                    collide = false;
                }
                /**
                 *内部小分支二：
                 *      如果add方法中条件4的通过cas设置cells[m%cells.length]位置的Cell对象中的value值设置为v+x失败,
                 *      说明已经发生竞争，将wasUncontended设置为true，跳出内部的if判断，最后重新计算一个新的probe，然后重新执行循环;
                 */
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash

                /**
                 *内部小分支三：新的争用线程参与争用的情况：处理刚进入当前方法时threadLocalRandomProbe=0的情况，
                 * 也就是当前线程第一次参与cell争用的cas失败，这里会尝试将x值加到cells[m%cells.length]的value ，如果成功直接退出
                 */
                else if (a.cas(v = a.value, ((fn == null) ? v + x :
                                             fn.applyAsLong(v, x))))
                    break;
                /**
                 *内部小分支四：分支3处理新的线程争用执行失败了，这时如果cells数组的长度已经到了最大值（大于等于cup数量），
                 * 或者是当前cells已经做了扩容，则将collide设置为false，后面重新计算prob的值
                 */
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                /**
                 *内部小分支五：如果发生了冲突collide=false，则设置其为true；会在最后重新计算hash值后，进入下一次for循环
                 */
                else if (!collide)
                    //设置冲突标志，表示发生了冲突，需要再次生成hash，重试。 如果下次重试任然走到了改分支此时collide=true，!collide条件不成立，则走后一个分支
                    collide = true;
                /**
                 *内部小分支六：扩容cells数组，新参与cell争用的线程两次均失败，且符合扩容条件，会执行该分支
                 */
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        //检查cells是否已经被扩容
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }

                //为当前线程重新计算hash值(重新计算hash值，表明下一次循环可能在cells其它位置竞争，而不会在当前同一cells位置一直竞争)
                h = advanceProbe(h);
            }

            //=================主分支二========================

            //这个大的分支处理add方法中的条件1与条件2成立的情况，如果cell表还未初始化或者长度为0，先尝试获取cellsBusy锁。
            // 完成cells初始化操作。
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    //初始化cells数组，初始容量为2,并将x值通过hash&1，放到0个或第1个位置上
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(x);
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    //如果init为true说明初始化成功，跳出循环
                    break;
            }
            //=================主分支三======================
            /**
             *  如果以上操作都失败了，则尝试将值累加到base上；
             *  如果还是失败，则重新进行循环
             */
            else if (casBase(v = base, ((fn == null) ? v + x :
                                        fn.applyAsLong(v, x))))
                break;                          // Fall back on using base
        }
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
        for (;;) {
            Cell[] as; Cell a; int n; long v;
            if ((as = cells) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (a.cas(v = a.value,
                               ((fn == null) ?
                                Double.doubleToRawLongBits
                                (Double.longBitsToDouble(v) + x) :
                                Double.doubleToRawLongBits
                                (fn.applyAsDouble
                                 (Double.longBitsToDouble(v), x)))))
                    break;
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            }
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            else if (casBase(v = base,
                             ((fn == null) ?
                              Double.doubleToRawLongBits
                              (Double.longBitsToDouble(v) + x) :
                              Double.doubleToRawLongBits
                              (fn.applyAsDouble
                               (Double.longBitsToDouble(v), x)))))
                break;                          // Fall back on using base
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long BASE;
    private static final long CELLSBUSY;
    private static final long PROBE;
    static {
        try {
            //获取unsafe实例
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> sk = Striped64.class;
            //获取Striped64类里面base变量在Striped64实例里面偏移量
            BASE = UNSAFE.objectFieldOffset
                (sk.getDeclaredField("base"));
            //获取Striped64类里面cellsBusy变量在Striped64实例里面偏移量
            CELLSBUSY = UNSAFE.objectFieldOffset
                (sk.getDeclaredField("cellsBusy"));
            Class<?> tk = Thread.class;
            //获取Striped64类里面threadLocalRandomProbe变量在Striped64实例里面偏移量
            PROBE = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("threadLocalRandomProbe"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
