//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package sun.misc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

/**
 * 1、历史背景：
 *      Java最初被设计为一种安全的受控环境。尽管如此，HotSpot从从2004年的JDK1.4之后还是包含了一个后门sun.misc.Unsafe，
 *      提供了一些可以直接操控内存和线程的底层操作。Java9中，为了提高JVM的可维护性，Unsafe和许多其他的东西一起都被作为内部使用类隐藏起来了。
 * 2、出现原因:
 *      做一些Java语言不允许但是又十分有用的事情(直接调用操作系统底层细节，此操作也可以通过JNI完成)
 * 3、Unsafe类是如此地不安全，以至于JDK开发者增加了很多特殊限制来访问它。
 * (1)私有的构造器
 * (2)工厂方法getUnsafe()的调用器只能被Bootloader加载，否则抛出SecurityException 异常
 *  但Unsafe中包含一个自引用域theUnsafe，可以通过反射获取此类实例
 *
 *  4、Unsafe一些功能：
 *  (1)、通过Unsafe类可以分配内存，可以释放内存；
 *        类中提供的3个本地方法allocateMemory、reallocateMemory、freeMemory分别用于
 *        分配内存，扩充内存和释放内存，与C语言中的3个方法对应。
 *
 *  (2)、可以定位对象某字段的内存位置
 *  (3)、修改对象的字段值，即使它是私有的；
 *  (4)、线程的挂起与恢复
 *  (5)、Compare And Swap(CAS)操作
 *
 *
 *  一、内存管理。包括分配内存、释放内存等。
 *
 *      该部分包括了allocateMemory（分配内存）、reallocateMemory（重新分配内存）、copyMemory（拷贝内存）
 *      、freeMemory（释放内存 ）、getAddress（获取内存地址）、addressSize、pageSize、getInt（获取内存地址指向的整数）
 *      、getIntVolatile（获取内存地址指向的整数，并支持volatile语义）、putInt（将整数写入指定内存地址）
 *      、putIntVolatile（将整数写入指定内存地址，并支持volatile语义）、putOrderedInt（将整数写入指定内存地址、有序或者有延迟的方法）等方法。
 *      getXXX和putXXX包含了各种基本类型的操作。利用copyMemory方法，我们可以实现一个通用的对象拷贝方法，无需再对每一个对象都实现clone方法，
 *      当然这通用的方法只能做到对象浅拷贝。
 *
 * 二、非常规的对象实例化。
 *
 *      allocateInstance()方法提供了另一种创建实例的途径。通常我们可以用new或者反射来实例化对象，使用allocateInstance()方法可以直接生成对象实例，
 *      且无需调用构造方法和其它初始化方法。这在对象反序列化的时候会很有用，能够重建和设置final字段，而不需要调用构造方法。
 *
 * 三、操作类、对象、变量。
 *
 *      这部分包括了staticFieldOffset（静态域偏移）、defineClass（定义类）、defineAnonymousClass（定义匿名类）
 *      、ensureClassInitialized（确保类初始化）、objectFieldOffset（对象域偏移）等方法。
 *      通过这些方法我们可以获取对象的指针，通过对指针进行偏移，我们不仅可以直接修改指针指向的数据（即使它们是私有的），
 *      甚至可以找到JVM已经认定为垃圾、可以进行回收的对象。
 *
 * 四、数组操作。
 *
 *      这部分包括了arrayBaseOffset（获取数组第一个元素的偏移地址）、arrayIndexScale（获取数组中元素的增量地址）等方法。
 *      arrayBaseOffset与arrayIndexScale配合起来使用，就可以定位数组中每个元素在内存中的位置。
 *      由于Java的数组最大值为Integer.MAX_VALUE，使用Unsafe类的内存分配方法可以实现超大数组。
 *      实际上这样的数据就可以认为是C数组，因此需要注意在合适的时间释放内存。
 *
 * 五、多线程同步。包括锁机制、CAS操作等。
 *
 *  这部分包括了monitorEnter、tryMonitorEnter、monitorExit、compareAndSwapInt、compareAndSwap等方法。
 *  其中monitorEnter、tryMonitorEnter、monitorExit已经被标记为deprecated，不建议使用。
 *  Unsafe类的CAS操作可能是用的最多的，它为Java的锁机制提供了一种新的解决办法，比如AtomicInteger等类都是通过该方法来实现的。
 *  compareAndSwap方法是原子的，可以避免繁重的锁机制，提高代码效率。这是一种乐观锁，通常认为在大部分情况下不出现竞态条件，如果操作失败，会不断重试直到成功。
 *
 * 六、挂起与恢复。
 *
 *      这部分包括了park、unpark等方法。
 *      将一个线程进行挂起是通过park方法实现的，调用 park后，线程将一直阻塞直到超时或者中断等条件出现。unpark可以终止一个挂起的线程，使其恢复正常。
 *      整个并发框架中对线程的挂起操作被封装在 LockSupport类中，LockSupport类中有各种版本pack方法，但最终都调用了Unsafe.park()方法。
 *
 * 七、内存屏障。
 *      这部分包括了loadFence、storeFence、fullFence等方法。这是在Java 8新引入的，用于定义内存屏障，避免代码重排序。
 *      loadFence() 表示该方法之前的所有load操作在内存屏障之前完成。同理storeFence()表示该方法之前的所有store操作在内存屏障之前完成。
 *      fullFence()表示该方法之前的所有load、store操作在内存屏障之前完成。
 *
 *
 *
 */
/***
 * This class should provide access to low-level operations and its
 * use should be limited to trusted code.  Fields can be accessed using
 * memory addresses, with undefined behaviour occurring if invalid memory
 * addresses are given.
 *
 * 这个类提供了一个更底层的操作并且应该在受信任的代码中使用。可以通过内存地址
 * 存取fields,如果给出的内存地址是无效的那么会有一个不确定的运行表现。
 *
 * @author Tom Tromey (tromey@redhat.com)
 * @author Andrew John Hughes (gnu_andrew@member.fsf.org)
 * @since 1.4
 */
public final class Unsafe {
    // Singleton class.
    private static final Unsafe theUnsafe;
    public static final int INVALID_FIELD_OFFSET = -1;
    public static final int ARRAY_BOOLEAN_BASE_OFFSET;
    public static final int ARRAY_BYTE_BASE_OFFSET;
    public static final int ARRAY_SHORT_BASE_OFFSET;
    public static final int ARRAY_CHAR_BASE_OFFSET;
    public static final int ARRAY_INT_BASE_OFFSET;
    public static final int ARRAY_LONG_BASE_OFFSET;
    public static final int ARRAY_FLOAT_BASE_OFFSET;
    public static final int ARRAY_DOUBLE_BASE_OFFSET;
    public static final int ARRAY_OBJECT_BASE_OFFSET;
    public static final int ARRAY_BOOLEAN_INDEX_SCALE;
    public static final int ARRAY_BYTE_INDEX_SCALE;
    public static final int ARRAY_SHORT_INDEX_SCALE;
    public static final int ARRAY_CHAR_INDEX_SCALE;
    public static final int ARRAY_INT_INDEX_SCALE;
    public static final int ARRAY_LONG_INDEX_SCALE;
    public static final int ARRAY_FLOAT_INDEX_SCALE;
    public static final int ARRAY_DOUBLE_INDEX_SCALE;
    public static final int ARRAY_OBJECT_INDEX_SCALE;
    public static final int ADDRESS_SIZE;

    private static native void registerNatives();
    /***
     * Private default constructor to prevent creation of an arbitrary
     * number of instances.
     * 使用私有默认构造器防止创建多个实例
     */
    private Unsafe() {
    }
    /***
     * Retrieve the singleton instance of <code>Unsafe</code>.  The calling
     * method should guard this instance from untrusted code, as it provides
     * access to low-level operations such as direct memory access.
     * 获取<code>Unsafe</code>的单例,这个方法调用应该防止在不可信的代码中实例，
     * 因为unsafe类提供了一个低级别的操作，例如直接内存存取。
     * @throws SecurityException if a security manager exists and prevents
     * access to the system properties.
     * 如果安全管理器不存在或者禁止访问系统属性
     */
    @CallerSensitive
    public static Unsafe getUnsafe() {
        Class var0 = Reflection.getCallerClass();
        if (!VM.isSystemDomainLoader(var0.getClassLoader())) {
            throw new SecurityException("Unsafe");
        } else {
            return theUnsafe;
        }
    }

    public native int getInt(Object var1, long var2);

    public native void putInt(Object var1, long var2, int var4);

    public native Object getObject(Object var1, long var2);

    public native void putObject(Object var1, long var2, Object var4);

    public native boolean getBoolean(Object var1, long var2);

    public native void putBoolean(Object var1, long var2, boolean var4);

    public native byte getByte(Object var1, long var2);

    public native void putByte(Object var1, long var2, byte var4);

    public native short getShort(Object var1, long var2);

    public native void putShort(Object var1, long var2, short var4);

    public native char getChar(Object var1, long var2);

    public native void putChar(Object var1, long var2, char var4);
    /***
     * Retrieves the value of the long field at the specified offset in the
     * supplied object.
     * 获取obj对象中offset偏移地址对应的long型field的值
     *
     * @param obj the object containing the field to read.
     *    包含需要去读取的field的对象
     * @param offset the offset of the long field within <code>obj</code>.
     *       <code>obj</code>中long型field的偏移量
     * @see #getLongVolatile(Object,long)
     */
    public native long getLong(Object var1, long var2);
    /***
     * Sets the value of the long field at the specified offset in the
     * supplied object to the given value.
     * 设置obj对象中offset偏移地址对应的long型field的值为指定值。
     *
     * @param obj the object containing the field to modify.
     *     包含需要修改field的对象
     * @param offset the offset of the long field within <code>obj</code>.
     *     <code>obj</code>中long型field的偏移量
     * @param value the new value of the field.
     *     field将被设置的新值
     * @see #putLongVolatile(Object,long,long)
     */
    public native void putLong(Object var1, long var2, long var4);

    public native float getFloat(Object var1, long var2);

    public native void putFloat(Object var1, long var2, float var4);

    public native double getDouble(Object var1, long var2);

    public native void putDouble(Object var1, long var2, double var4);

    /** @deprecated */
    @Deprecated
    public int getInt(Object var1, int var2) {
        return this.getInt(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putInt(Object var1, int var2, int var3) {
        this.putInt(var1, (long)var2, var3);
    }

    /** @deprecated */
    @Deprecated
    public Object getObject(Object var1, int var2) {
        return this.getObject(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putObject(Object var1, int var2, Object var3) {
        this.putObject(var1, (long)var2, var3);
    }

    /** @deprecated */
    @Deprecated
    public boolean getBoolean(Object var1, int var2) {
        return this.getBoolean(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putBoolean(Object var1, int var2, boolean var3) {
        this.putBoolean(var1, (long)var2, var3);
    }

    /** @deprecated */
    @Deprecated
    public byte getByte(Object var1, int var2) {
        return this.getByte(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putByte(Object var1, int var2, byte var3) {
        this.putByte(var1, (long)var2, var3);
    }

    /** @deprecated */
    @Deprecated
    public short getShort(Object var1, int var2) {
        return this.getShort(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putShort(Object var1, int var2, short var3) {
        this.putShort(var1, (long)var2, var3);
    }

    /** @deprecated */
    @Deprecated
    public char getChar(Object var1, int var2) {
        return this.getChar(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putChar(Object var1, int var2, char var3) {
        this.putChar(var1, (long)var2, var3);
    }

    /** @deprecated */
    @Deprecated
    public long getLong(Object var1, int var2) {
        return this.getLong(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putLong(Object var1, int var2, long var3) {
        this.putLong(var1, (long)var2, var3);
    }

    /** @deprecated */
    @Deprecated
    public float getFloat(Object var1, int var2) {
        return this.getFloat(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putFloat(Object var1, int var2, float var3) {
        this.putFloat(var1, (long)var2, var3);
    }

    /** @deprecated */
    @Deprecated
    public double getDouble(Object var1, int var2) {
        return this.getDouble(var1, (long)var2);
    }

    /** @deprecated */
    @Deprecated
    public void putDouble(Object var1, int var2, double var3) {
        this.putDouble(var1, (long)var2, var3);
    }

    public native byte getByte(long var1);

    public native void putByte(long var1, byte var3);

    public native short getShort(long var1);

    public native void putShort(long var1, short var3);

    public native char getChar(long var1);

    public native void putChar(long var1, char var3);

    public native int getInt(long var1);

    public native void putInt(long var1, int var3);

    public native long getLong(long var1);

    public native void putLong(long var1, long var3);

    public native float getFloat(long var1);

    public native void putFloat(long var1, float var3);

    public native double getDouble(long var1);

    public native void putDouble(long var1, double var3);

    public native long getAddress(long var1);

    public native void putAddress(long var1, long var3);

    public native long allocateMemory(long var1);

    public native long reallocateMemory(long var1, long var3);

    public native void setMemory(Object var1, long var2, long var4, byte var6);

    public void setMemory(long var1, long var3, byte var5) {
        this.setMemory((Object)null, var1, var3, var5);
    }

    public native void copyMemory(Object var1, long var2, Object var4, long var5, long var7);

    public void copyMemory(long var1, long var3, long var5) {
        this.copyMemory((Object)null, var1, (Object)null, var3, var5);
    }

    public native void freeMemory(long var1);

    /** @deprecated */
    @Deprecated
    public int fieldOffset(Field var1) {
        return Modifier.isStatic(var1.getModifiers()) ? (int)this.staticFieldOffset(var1) : (int)this.objectFieldOffset(var1);
    }

    /** @deprecated */
    @Deprecated
    public Object staticFieldBase(Class<?> var1) {
        Field[] var2 = var1.getDeclaredFields();

        for(int var3 = 0; var3 < var2.length; ++var3) {
            if (Modifier.isStatic(var2[var3].getModifiers())) {
                return this.staticFieldBase(var2[var3]);
            }
        }

        return null;
    }

    public native long staticFieldOffset(Field var1);

    /***
     * Returns the memory address offset of the given static field.
     * The offset is merely used as a means to access a particular field
     * in the other methods of this class.  The value is unique to the given
     * field and the same value should be returned on each subsequent call.
     * 返回指定静态field的内存地址偏移量,在这个类的其他方法中这个值只是被用作一个访问
     * 特定field的一个方式。这个值对于 给定的field是唯一的，并且后续对该方法的调用都应该
     * 返回相同的值。
     *
     * @param var1 the field whose offset should be returned.
     *              需要返回偏移量的field
     * @return the offset of the given field.
     *         指定field的偏移量
     */
    public native long objectFieldOffset(Field var1);

    public native Object staticFieldBase(Field var1);

    public native boolean shouldBeInitialized(Class<?> var1);

    public native void ensureClassInitialized(Class<?> var1);
    /***
     * Returns the offset of the first element for a given array class.
     * To access elements of the array class, this value may be used along with
     * with that returned by
     * <a href="#arrayIndexScale"><code>arrayIndexScale</code></a>,
     * if non-zero.
     * 获取给定数组中第一个元素的偏移地址。
     * 为了存取数组中的元素，这个偏移地址与<a href="#arrayIndexScale"><code>arrayIndexScale
     * </code></a>方法的非0返回值一起被使用。
     * @param arrayClass the class for which the first element's address should
     *                   be obtained.
     *                   第一个元素地址被获取的class
     * @return the offset of the first element of the array class.
     *    数组第一个元素 的偏移地址
     * @see arrayIndexScale(Class<?>)
     */
    public native int arrayBaseOffset(Class<?> arrayClass);
    /***
     * Returns the scale factor used for addressing elements of the supplied
     * array class.  Where a suitable scale factor can not be returned (e.g.
     * for primitive types), zero should be returned.  The returned value
     * can be used with
     * <a href="#arrayBaseOffset"><code>arrayBaseOffset</code></a>
     * to access elements of the class.
     * 获取用户给定数组寻址的换算因子.一个合适的换算因子不能返回的时候(例如：基本类型),
     * 返回0.这个返回值能够与<a href="#arrayBaseOffset"><code>arrayBaseOffset</code>
     * </a>一起使用去存取这个数组class中的元素
     *
     * @param arrayClass the class whose scale factor should be returned.
     * @return the scale factor, or zero if not supported for this array class.
     */
    public native int arrayIndexScale(Class<?> arrayClass);

    public native int addressSize();

    public native int pageSize();

    public native Class<?> defineClass(String var1, byte[] var2, int var3, int var4, ClassLoader var5, ProtectionDomain var6);

    public native Class<?> defineAnonymousClass(Class<?> var1, byte[] var2, Object[] var3);

    /**
     * 不使用构造函数给指定类创建一个实例
     * @param var1
     * @return
     * @throws InstantiationException
     */
    public native Object allocateInstance(Class<?> var1) throws InstantiationException;

    /** @deprecated */
    @Deprecated
    public native void monitorEnter(Object var1);

    /** @deprecated */
    @Deprecated
    public native void monitorExit(Object var1);

    /** @deprecated */
    @Deprecated
    public native boolean tryMonitorEnter(Object var1);

    public native void throwException(Throwable var1);
    /***
     * Compares the value of the object field at the specified offset
     * in the supplied object with the given expected value, and updates
     * it if they match.  The operation of this method should be atomic,
     * thus providing an uninterruptible way of updating an object field.
     * 在obj的offset位置比较object field和期望的值，如果相同则更新。这个方法
     * 的操作应该是原子的，因此提供了一种不可中断的方式更新object field。
     *
     * @param obj the object containing the field to modify.
     *         包含要修改field的对象
     * @param offset the offset of the object field within <code>obj</code>.
     *         <code>obj</code>中object型field的偏移量
     * @param expect the expected value of the field.
     *               希望field中存在的值
     * @param update the new value of the field if it equals <code>expect</code>.
     *               如果期望值expect与field的当前值相同，设置filed的值为这个新值
     * @return true if the field was changed.
     *              如果field的值被更改
     */
    public final native boolean compareAndSwapObject(Object obj, long offset, Object expect, Object update);

    /***
     * Compares the value of the integer field at the specified offset
     * in the supplied object with the given expected value, and updates
     * it if they match.  The operation of this method should be atomic,
     * thus providing an uninterruptible way of updating an integer field.
     * 在obj的offset位置比较integer field和期望的值，如果相同则更新。这个方法
     * 的操作应该是原子的，因此提供了一种不可中断的方式更新integer field。
     *
     * @param obj the object containing the field to modify.
     *            包含要修改field的对象
     * @param offset the offset of the integer field within <code>obj</code>.
     *               <code>obj</code>中整型field的偏移量
     * @param expect the expected value of the field.
     *               希望field中存在的值
     * @param update the new value of the field if it equals <code>expect</code>.
     *           如果期望值expect与field的当前值相同，设置filed的值为这个新值
     * @return true if the field was changed.
     *                             如果field的值被更改
     */
    public final native boolean compareAndSwapInt(Object obj, long offset, int expect, int update);
    /***
     * Compares the value of the long field at the specified offset
     * in the supplied object with the given expected value, and updates
     * it if they match.  The operation of this method should be atomic,
     * thus providing an uninterruptible way of updating a long field.
     * 在obj的offset位置比较long field和期望的值，如果相同则更新。这个方法
     * 的操作应该是原子的，因此提供了一种不可中断的方式更新long field。
     *
     * @param obj the object containing the field to modify.
     *              包含要修改field的对象
     * @param offset the offset of the long field within <code>obj</code>.
     *               <code>obj</code>中long型field的偏移量
     * @param expect the expected value of the field.
     *               希望field中存在的值
     * @param update the new value of the field if it equals <code>expect</code>.
     *               如果期望值expect与field的当前值相同，设置filed的值为这个新值
     * @return true if the field was changed.
     *              如果field的值被更改
     */
    public final native boolean compareAndSwapLong(Object obj, long offset, long expect, long update);
    /***
     * Retrieves the value of the object field at the specified offset in the
     * supplied object with volatile load semantics.
     * 获取obj对象中offset偏移地址对应的object型field的值,支持volatile load语义。
     *
     * @param obj the object containing the field to read.
     *    包含需要去读取的field的对象
     * @param offset the offset of the object field within <code>obj</code>.
     *       <code>obj</code>中object型field的偏移量
     */
    public native Object getObjectVolatile(Object obj, long offset);

    /***
     * Sets the value of the object field at the specified offset in the
     * supplied object to the given value, with volatile store semantics.
     * 设置obj对象中offset偏移地址对应的object型field的值为指定值。支持volatile store语义
     *
     * @param obj the object containing the field to modify.
     *    包含需要修改field的对象
     * @param offset the offset of the object field within <code>obj</code>.
     *     <code>obj</code>中object型field的偏移量
     * @param value the new value of the field.
     *       field将被设置的新值
     * @see #putObject(Object,long,Object)
     */
    public native void putObjectVolatile(Object obj, long offset, Object value);

    public native int getIntVolatile(Object var1, long var2);

    public native void putIntVolatile(Object var1, long var2, int var4);

    public native boolean getBooleanVolatile(Object var1, long var2);

    public native void putBooleanVolatile(Object var1, long var2, boolean var4);

    public native byte getByteVolatile(Object var1, long var2);

    public native void putByteVolatile(Object var1, long var2, byte var4);

    public native short getShortVolatile(Object var1, long var2);

    public native void putShortVolatile(Object var1, long var2, short var4);

    public native char getCharVolatile(Object var1, long var2);

    public native void putCharVolatile(Object var1, long var2, char var4);
    /***
     * Retrieves the value of the long field at the specified offset in the
     * supplied object with volatile load semantics.
     * 获取obj对象中offset偏移地址对应的long型field的值,支持volatile load语义。
     *
     * @param obj the object containing the field to read.
     *    包含需要去读取的field的对象
     * @param offset the offset of the long field within <code>obj</code>.
     *       <code>obj</code>中long型field的偏移量
     * @see #getLong(Object,long)
     */
    public native long getLongVolatile(Object var1, long var2);
    /***
     * Sets the value of the long field at the specified offset in the
     * supplied object to the given value, with volatile store semantics.
     * 设置obj对象中offset偏移地址对应的long型field的值为指定值。支持volatile store语义
     *
     * @param obj the object containing the field to modify.
     *            包含需要修改field的对象
     * @param offset the offset of the long field within <code>obj</code>.
     *               <code>obj</code>中long型field的偏移量
     * @param value the new value of the field.
     *              field将被设置的新值
     * @see #putLong(Object,long,long)
     */
    public native void putLongVolatile(Object obj, long offset, long value);

    public native float getFloatVolatile(Object var1, long var2);

    public native void putFloatVolatile(Object var1, long var2, float var4);

    public native double getDoubleVolatile(Object var1, long var2);

    public native void putDoubleVolatile(Object var1, long var2, double var4);

    public native void putOrderedObject(Object var1, long var2, Object var4);

    public native void putOrderedInt(Object var1, long var2, int var4);

    public native void putOrderedLong(Object var1, long var2, long var4);
    /***
     * Releases the block on a thread created by
     * <a href="#park"><code>park</code></a>.  This method can also be used
     * to terminate a blockage caused by a prior call to <code>park</code>.
     * This operation is unsafe, as the thread must be guaranteed to be
     * live.  This is true of Java, but not native code.
     * 释放被<a href="#park"><code>park</code></a>创建的在一个线程上的阻塞.这个
     * 方法也可以被使用来终止一个先前调用<code>park</code>导致的阻塞.
     * 这个操作操作时不安全的,因此线程必须保证是活的.这是java代码不是native代码。
     * @param thread the thread to unblock.
     *           要解除阻塞的线程
     */
    public native void unpark(Object thread);

    /***
     * Blocks the thread until a matching
     * <a href="#unpark"><code>unpark</code></a> occurs, the thread is
     * interrupted or the optional timeout expires.  If an <code>unpark</code>
     * call has already occurred, this also counts.  A timeout value of zero
     * is defined as no timeout.  When <code>isAbsolute</code> is
     * <code>true</code>, the timeout is in milliseconds relative to the
     * epoch.  Otherwise, the value is the number of nanoseconds which must
     * occur before timeout.  This call may also return spuriously (i.e.
     * for no apparent reason).
     * 阻塞一个线程直到<a href="#unpark"><code>unpark</code></a>出现、线程
     * 被中断或者timeout时间到期。如果一个<code>unpark</code>调用已经出现了，
     * 这里只计数。timeout为0表示永不过期.当<code>isAbsolute</code>为true时，
     * timeout是相对于新纪元之后的毫秒。否则这个值就是超时前的纳秒数。这个方法执行时
     * 也可能不合理地返回(没有具体原因)
     *
     * @param isAbsolute true if the timeout is specified in milliseconds from
     *                   the epoch.
     *                   如果为true timeout的值是一个相对于新纪元之后的毫秒数
     * @param time either the number of nanoseconds to wait, or a time in
     *             milliseconds from the epoch to wait for.
     *             可以是一个要等待的纳秒数，或者是一个相对于新纪元之后的毫秒数直到
     *             到达这个时间点
     */
    public native void park(boolean isAbsolute, long time);

    public native int getLoadAverage(double[] var1, int var2);

    public final int getAndAddInt(Object var1, long var2, int var4) {
        int var5;
        do {
            var5 = this.getIntVolatile(var1, var2);
        } while(!this.compareAndSwapInt(var1, var2, var5, var5 + var4));

        return var5;
    }

    public final long getAndAddLong(Object var1, long var2, long var4) {
        long var6;
        do {
            var6 = this.getLongVolatile(var1, var2);
        } while(!this.compareAndSwapLong(var1, var2, var6, var6 + var4));

        return var6;
    }

    public final int getAndSetInt(Object var1, long var2, int var4) {
        int var5;
        do {
            var5 = this.getIntVolatile(var1, var2);
        } while(!this.compareAndSwapInt(var1, var2, var5, var4));

        return var5;
    }

    public final long getAndSetLong(Object var1, long var2, long var4) {
        long var6;
        do {
            var6 = this.getLongVolatile(var1, var2);
        } while(!this.compareAndSwapLong(var1, var2, var6, var4));

        return var6;
    }

    public final Object getAndSetObject(Object var1, long var2, Object var4) {
        Object var5;
        do {
            var5 = this.getObjectVolatile(var1, var2);
        } while(!this.compareAndSwapObject(var1, var2, var5, var4));

        return var5;
    }

    public native void loadFence();

    public native void storeFence();

    public native void fullFence();

    private static void throwIllegalAccessError() {
        throw new IllegalAccessError();
    }

    static {
        registerNatives();
        Reflection.registerMethodsToFilter(Unsafe.class, new String[]{"getUnsafe"});
        theUnsafe = new Unsafe();
        ARRAY_BOOLEAN_BASE_OFFSET = theUnsafe.arrayBaseOffset(boolean[].class);
        ARRAY_BYTE_BASE_OFFSET = theUnsafe.arrayBaseOffset(byte[].class);
        ARRAY_SHORT_BASE_OFFSET = theUnsafe.arrayBaseOffset(short[].class);
        ARRAY_CHAR_BASE_OFFSET = theUnsafe.arrayBaseOffset(char[].class);
        ARRAY_INT_BASE_OFFSET = theUnsafe.arrayBaseOffset(int[].class);
        ARRAY_LONG_BASE_OFFSET = theUnsafe.arrayBaseOffset(long[].class);
        ARRAY_FLOAT_BASE_OFFSET = theUnsafe.arrayBaseOffset(float[].class);
        ARRAY_DOUBLE_BASE_OFFSET = theUnsafe.arrayBaseOffset(double[].class);
        ARRAY_OBJECT_BASE_OFFSET = theUnsafe.arrayBaseOffset(Object[].class);
        ARRAY_BOOLEAN_INDEX_SCALE = theUnsafe.arrayIndexScale(boolean[].class);
        ARRAY_BYTE_INDEX_SCALE = theUnsafe.arrayIndexScale(byte[].class);
        ARRAY_SHORT_INDEX_SCALE = theUnsafe.arrayIndexScale(short[].class);
        ARRAY_CHAR_INDEX_SCALE = theUnsafe.arrayIndexScale(char[].class);
        ARRAY_INT_INDEX_SCALE = theUnsafe.arrayIndexScale(int[].class);
        ARRAY_LONG_INDEX_SCALE = theUnsafe.arrayIndexScale(long[].class);
        ARRAY_FLOAT_INDEX_SCALE = theUnsafe.arrayIndexScale(float[].class);
        ARRAY_DOUBLE_INDEX_SCALE = theUnsafe.arrayIndexScale(double[].class);
        ARRAY_OBJECT_INDEX_SCALE = theUnsafe.arrayIndexScale(Object[].class);
        ADDRESS_SIZE = theUnsafe.addressSize();
    }
}
