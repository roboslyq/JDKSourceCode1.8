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
import java.io.Serializable;

/**
 * One or more variables that together maintain an initially zero
 * {@code long} sum.  When updates (method {@link #add}) are contended
 * across threads, the set of variables may grow dynamically to reduce
 * contention. Method {@link #sum} (or, equivalently, {@link
 * #longValue}) returns the current total combined across the
 * variables maintaining the sum.
 *
 * 使用一个(Base Long)或者多个变量(Cells[]数组)来维护一个初始化为0的long类型值。当通过多线程执行add()方法竞争较大时，
 * 变量的数组可以进行动态扩容。
 * Long的最终值 = Base Long + sum(Cells[])
 * 因此，每次N个线程并发同时更新时，将单个资源竞争压力分发到了cells.length个大小的资源竞争。大大减小了竞争压力。
 *
 * <p>This class is usually preferable to {@link AtomicLong} when
 * multiple threads update a common sum that is used for purposes such
 * as collecting statistics, not for fine-grained synchronization
 * control.  Under low update contention, the two classes have similar
 * characteristics. But under high contention, expected throughput of
 * this class is significantly higher, at the expense of higher space
 * consumption.
 *
 * LongAdder 功能类似 AtomicLong ，在低并发情况下二者表现差不多，
 * 在高并发情况下 LongAdder 的表现就会好很多。
 *
 * <p>LongAdders can be used with a {@link
 * java.util.concurrent.ConcurrentHashMap} to maintain a scalable
 * frequency map (a form of histogram or multiset). For example, to
 * add a count to a {@code ConcurrentHashMap<String,LongAdder> freqs},
 * initializing if not already present, you can use {@code
 * freqs.computeIfAbsent(k -> new LongAdder()).increment();}
 *
 * <p>This class extends {@link Number}, but does <em>not</em> define
 * methods such as {@code equals}, {@code hashCode} and {@code
 * compareTo} because instances are expected to be mutated, and so are
 * not useful as collection keys.
 *
 * LongAdder继承了Striped64类，来实现累加功能的,它是实现高并发累加的工具类；
 * Striped64的设计核心思路就是通过内部的分散计算来避免竞争。
 *
 * 1、LongAdder在高并发下比AutomicLong性能好的原因：
 *      （1）AtomicLong 是对整个 Long 值进行 CAS 操作，高并发竞争时失败可能性很大。
 *          而 LongAdder 是针对 Cell 数组的某个 Cell 进行 CAS 操作 ，把线程的名字的 hash 值，作为 Cell 数组的下标，
 *          然后对 Cell[i] 的 long 进行 CAS 操作。简单粗暴的分散了高并发下的竞争压力。
 * 2、使用方式：
 *         LongAdder longAdder = new LongAdder();
     *         longAdder.add(2);
     *         longAdder.add(3);
     *         longAdder.add(5);
     *         longAdder.add(6);
 *         System.out.println(longAdder.longValue());
 *   最后打印结果为： 16
 * @since 1.8
 * @author Doug Lea
 */
public class LongAdder extends Striped64 implements Serializable {

    private static final long serialVersionUID = 7249069246863182397L;

    /**
     * Creates a new adder with initial sum of zero.
     * 初始化一个值为0的base。类属性默认初始化为0，可以不需要手动赋值。
     */
    public LongAdder() {
    }

    /**
     * Adds the given value.
     *
     * 1、如果 cells 数组不为空，对参数进行 casBase 操作。如果 casBase 操作失败，基本是由于多线程(并发)导致的资源竞争而导致的。
     *    并且竞争还比较激烈。此时，进入第二步。
     *
     * 2、如果 cells 为空，表明当前的LongAdder的Cells还没有进行初始化，那么直接进入longAccumulate()进么cells相关维护（初始化cells）;
     *
     * 3、m = cells 数组长度减一，如果数组长度小于 1，则进入 longAccumulate()（更新cells大小）。
     *
     * 4、如果都没有满足以上条件，则对当前线程进行某种 hash 生成一个数组下标，对下标保存的值进行 cas 操作。
     *      如果操作失败，则说明竞争依然激烈，则进入 longAccumulate()(在cells中完成add()相关操作).
     *
     * 上面4步骤基本思想是：
     *          操作的核心思想还是基于 cas。但是 cas 失败后，并不是傻乎乎的自旋，
     *          而是逐渐升级。升级的 cas 都不管用了则进入 longAccumulate() 这个方法。
     */
    public void add(long x) {
        /*
         * 1、Cell 是 java.util.concurrent.atomic 下 Striped64 的一个内部类。
         * 2、其实一个 Cell 的本质就是一个 volatile 修饰的 long 值，且这个值能够进行 cas 操作。
         */
        Cell[] as;
        long b, // Long的原值
             v; //
        int  m;//保存(as.length - 1)
        Cell a;
        /**
         * 如果一下两种条件则继续执行if内的语句
         * 1. cells数组不为null（不存在争用的时候，cells数组一定为null，一旦对base的cas操作失败，才会初始化cells数组）
         * 2. 如果cells数组为null，如果casBase执行成功(表示取得锁，更新成功)，则直接返回，
         *    如果casBase方法执行失败（casBase失败，说明第一次争用冲突产生，需要对cells数组初始化）进入if内；
         */
        if (    (as = cells) != null
                || !casBase(b = base, b + x)
                ) {

            // uncontended判断cells数组中，当前线程要做cas累加操作的某个元素是否不存在争用，如果cas失败则存在争用；
            // uncontended = false代表存在争用，uncontended = true代表不存在争用。
            boolean uncontended = true;

            /**
             * 此if判断条件十分精髓，条件关系层层递进 ^_^ 。
             *
             *1. as == null ：
             *      cells数组未被初始化，成立则直接进入if执行cell初始化
             *
             *2. (m = as.length - 1) < 0 ：
             *      cells数组的长度为0
             *
             *条件1与2都代表cells数组没有被初始化成功，初始化成功的cells数组长度为2；
             *
             *3. (a = as[getProbe() & m]) == null ：
             *      如果cells被初始化，且它的长度不为0，则通过getProbe方法获取当前线程Thread的threadLocalRandomProbe变量的值，
             *      初始为0，然后执行threadLocalRandomProbe&(cells.length-1 ),相当于m%cells.length;如果cells[threadLocalRandomProbe%cells.length]
             *      的位置为null，这说明这个位置从来没有线程做过累加，需要进入if继续执行，在这个位置创建一个新的Cell对象；
             *
             *4.!(uncontended = a.cas(v = a.value, v + x))：
             *       如果到这一个条件，证明第3个条件为flase，那么可以直接尝试进行相关的CAS操作，如果成功则直接返回。
             *       尝试对cells[threadLocalRandomProbe%cells.length]位置的Cell对象中的value值做累加操作,并返回操作结果,
             *       如果失败了则进入if，重新计算一个threadLocalRandomProbe；如果进入if语句执行longAccumulate方法,
             *
             * 有三种情况
             *    1. 前两个条件代表cells没有初始化，
             *    2. 第三个条件指当前线程hash到的cells数组中的位置还没有其它线程做过累加操作，
             *    3. 第四个条件代表产生了冲突,uncontended=false（没有竞争为flase，表示有竞争）
             **/
            if (as == null
                    || (m = as.length - 1) < 0
                    || (a = as[getProbe() & m]) == null
                    || !(uncontended = a.cas(v = a.value, v + x))
                    ){
                //cells进行相关操作，是LongAdder最核心的方法。
                longAccumulate(x, null, uncontended);
            }
        }
    }

    /**
     * Equivalent to {@code add(1)}.
     */
    public void increment() {
        add(1L);
    }

    /**
     * Equivalent to {@code add(-1)}.
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * Returns the current sum.  The returned value is <em>NOT</em> an
     * atomic snapshot; invocation in the absence of concurrent
     * updates returns an accurate result, but concurrent updates that
     * occur while the sum is being calculated might not be
     * incorporated.
     *
     * @return the sum
     */
    public long sum() {
        Cell[] as = cells; Cell a;
        long sum = base;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    sum += a.value;
            }
        }
        return sum;
    }

    /**
     * Resets variables maintaining the sum to zero.  This method may
     * be a useful alternative to creating a new adder, but is only
     * effective if there are no concurrent updates.  Because this
     * method is intrinsically racy, it should only be used when it is
     * known that no threads are concurrently updating.
     */
    public void reset() {
        Cell[] as = cells; Cell a;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    a.value = 0L;
            }
        }
    }

    /**
     * Equivalent in effect to {@link #sum} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     *
     * @return the sum
     */
    public long sumThenReset() {
        Cell[] as = cells; Cell a;
        long sum = base;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null) {
                    sum += a.value;
                    a.value = 0L;
                }
            }
        }
        return sum;
    }

    /**
     * Returns the String representation of the {@link #sum}.
     * @return the String representation of the {@link #sum}
     */
    public String toString() {
        return Long.toString(sum());
    }

    /**
     * Equivalent to {@link #sum}.
     *
     * @return the sum
     */
    public long longValue() {
        return sum();
    }

    /**
     * Returns the {@link #sum} as an {@code int} after a narrowing
     * primitive conversion.
     */
    public int intValue() {
        return (int)sum();
    }

    /**
     * Returns the {@link #sum} as a {@code float}
     * after a widening primitive conversion.
     */
    public float floatValue() {
        return (float)sum();
    }

    /**
     * Returns the {@link #sum} as a {@code double} after a widening
     * primitive conversion.
     */
    public double doubleValue() {
        return (double)sum();
    }

    /**
     * Serialization proxy, used to avoid reference to the non-public
     * Striped64 superclass in serialized forms.
     * @serial include
     */
    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by sum().
         * @serial
         */
        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }

        /**
         * Return a {@code LongAdder} object with initial state
         * held by this proxy.
         *
         * @return a {@code LongAdder} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.base = value;
            return a;
        }
    }

    /**
     * Returns a
     * <a href="../../../../serialized-form.html#java.util.concurrent.atomic.LongAdder.SerializationProxy">
     * SerializationProxy</a>
     * representing the state of this instance.
     *
     * @return a {@link SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
