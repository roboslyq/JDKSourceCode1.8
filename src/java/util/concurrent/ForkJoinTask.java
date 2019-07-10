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

package java.util.concurrent;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.reflect.Constructor;

/**
 *
 *  1、ForkJoinTask简介
 *      ForkJoinTask首先是一个任务，是对任务的封装。它实现了 Future 接口，说明它也是一个可取消的异步运算任务，实际上ForkJoinTask
 *      是 Future 的轻量级实现，主要用在纯粹是计算的函数式任务或者操作完全独立的对象计算任务。
 *  2、fork 是主运行方法，用于异步执行,而 join 方法在任务结果计算完毕之后才会运行，用来合并或返回计算结果。
 *     其内部类都比较简单，ExceptionNode 是用于存储任务执行期间的异常信息的单向链表；
 *  3、其余四个类是为 Runnable/Callable 任务提供的适配器类，用于把 Runnable/Callable 转化为 ForkJoinTask 类型的任务
 *  （因为 ForkJoinPool 只可以运行 ForkJoinTask 类型的任务）。
 *
 * 整个流程和重要方法归纳如下：
 * 1、任务提交
 *
 * 提交任务入口：submit,execute,invoke
 * 完整版提交任务：externalSubmit(包括初始化)
 * 简单版提交任务：externalPush
 *
 * 2、worker管理
 *
 * 激活或创建：signalWork
 * 创建：tryAddWorker,createWorker
 * 注册、撤销注册：registerWorker,deregisterWorker
 *
 *3、 worker执行(runWorker三部曲)
 *
 * 获取：scan
 * 执行：runTask
 * 等待：awaitWork
 *
 * 4、Fork
 *
 * 等同于提交任务
 *
 * 5、Join(doJoin)
 *
 * 当前不是worker：externalAwaitDone
 * 当前是worker：awaitJoin
 *
 * 6、awaitJoin等待两种策略
 *
 * Helping：tryRemoveAndExec、helpStealer
 * Compensating：tryCompensate
 *
 * 7、等待所有任务完成
 *
 * 静止：awaitQuiescence
 * 终止：awaitTermination
 *
 * 8、关闭
 *
 * shutdown,shutdownNow
 * tryTerminate
 *
 * 异常处理
 *
 * Abstract base class for tasks that run within a {@link ForkJoinPool}.
 * A {@code ForkJoinTask} is a thread-like entity that is much
 * lighter weight than a normal thread.  Huge numbers of tasks and
 * subtasks may be hosted by a small number of actual threads in a
 * ForkJoinPool, at the price of some usage limitations.
 * 在{@link ForkJoinPool}中运行的任务的抽象基类。一个{@code ForkJoinTask}可以认为是“类线程”的实体但比真正的线程轻量级很多。
 * 大量的任务和子任务可以被ForkJoinPool中少量的真实线程(Thread)运行。但这些方案是有一些场景使用限制的。
 * <p>A "main" {@code ForkJoinTask} begins execution when it is
 * explicitly submitted to a {@link ForkJoinPool}, or, if not already
 * engaged in a ForkJoin computation, commenced in the {@link
 * ForkJoinPool#commonPool()} via {@link #fork}, {@link #invoke}, or
 * related methods.  Once started, it will usually in turn start other
 * subtasks.  As indicated by the name of this class, many programs
 * using {@code ForkJoinTask} employ only methods {@link #fork} and
 * {@link #join}, or derivatives such as {@link
 * #invokeAll(ForkJoinTask...) invokeAll}.  However, this class also
 * provides a number of other methods that can come into play in
 * advanced usages, as well as extension mechanics that allow support
 * of new forms of fork/join processing.
 *
 * <p>A {@code ForkJoinTask} is a lightweight form of {@link Future}.
 * The efficiency of {@code ForkJoinTask}s stems from a set of
 * restrictions (that are only partially statically enforceable)
 * reflecting their main use as computational tasks calculating pure
 * functions or operating on purely isolated objects.  The primary
 * coordination mechanisms are {@link #fork}, that arranges
 * asynchronous execution, and {@link #join}, that doesn't proceed
 * until the task's result has been computed.  Computations should
 * ideally avoid {@code synchronized} methods or blocks, and should
 * minimize other blocking synchronization apart from joining other
 * tasks or using synchronizers such as Phasers that are advertised to
 * cooperate with fork/join scheduling. Subdividable tasks should also
 * not perform blocking I/O, and should ideally access variables that
 * are completely independent of those accessed by other running
 * tasks. These guidelines are loosely enforced by not permitting
 * checked exceptions such as {@code IOExceptions} to be
 * thrown. However, computations may still encounter unchecked
 * exceptions, that are rethrown to callers attempting to join
 * them. These exceptions may additionally include {@link
 * RejectedExecutionException} stemming from internal resource
 * exhaustion, such as failure to allocate internal task
 * queues. Rethrown exceptions behave in the same way as regular
 * exceptions, but, when possible, contain stack traces (as displayed
 * for example using {@code ex.printStackTrace()}) of both the thread
 * that initiated the computation as well as the thread actually
 * encountering the exception; minimally only the latter.
 *
 * <p>It is possible to define and use ForkJoinTasks that may block,
 * but doing do requires three further considerations: (1) Completion
 * of few if any <em>other</em> tasks should be dependent on a task
 * that blocks on external synchronization or I/O. Event-style async
 * tasks that are never joined (for example, those subclassing {@link
 * CountedCompleter}) often fall into this category.  (2) To minimize
 * resource impact, tasks should be small; ideally performing only the
 * (possibly) blocking action. (3) Unless the {@link
 * ForkJoinPool.ManagedBlocker} API is used, or the number of possibly
 * blocked tasks is known to be less than the pool's {@link
 * ForkJoinPool#getParallelism} level, the pool cannot guarantee that
 * enough threads will be available to ensure progress or good
 * performance.
 *
 * <p>The primary method for awaiting completion and extracting
 * results of a task is {@link #join}, but there are several variants:
 * The {@link Future#get} methods support interruptible and/or timed
 * waits for completion and report results using {@code Future}
 * conventions. Method {@link #invoke} is semantically
 * equivalent to {@code fork(); join()} but always attempts to begin
 * execution in the current thread. The "<em>quiet</em>" forms of
 * these methods do not extract results or report exceptions. These
 * may be useful when a set of tasks are being executed, and you need
 * to delay processing of results or exceptions until all complete.
 * Method {@code invokeAll} (available in multiple versions)
 * performs the most common form of parallel invocation: forking a set
 * of tasks and joining them all.
 *
 * <p>In the most typical usages, a fork-join pair act like a call
 * (fork) and return (join) from a parallel recursive function. As is
 * the case with other forms of recursive calls, returns (joins)
 * should be performed innermost-first. For example, {@code a.fork();
 * b.fork(); b.join(); a.join();} is likely to be substantially more
 * efficient than joining {@code a} before {@code b}.
 *
 * <p>The execution status of tasks may be queried at several levels
 * of detail: {@link #isDone} is true if a task completed in any way
 * (including the case where a task was cancelled without executing);
 * {@link #isCompletedNormally} is true if a task completed without
 * cancellation or encountering an exception; {@link #isCancelled} is
 * true if the task was cancelled (in which case {@link #getException}
 * returns a {@link java.util.concurrent.CancellationException}); and
 * {@link #isCompletedAbnormally} is true if a task was either
 * cancelled or encountered an exception, in which case {@link
 * #getException} will return either the encountered exception or
 * {@link java.util.concurrent.CancellationException}.
 *
 * <p>The ForkJoinTask class is not usually directly subclassed.
 * Instead, you subclass one of the abstract classes that support a
 * particular style of fork/join processing, typically {@link
 * RecursiveAction} for most computations that do not return results,
 * {@link RecursiveTask} for those that do, and {@link
 * CountedCompleter} for those in which completed actions trigger
 * other actions.  Normally, a concrete ForkJoinTask subclass declares
 * fields comprising its parameters, established in a constructor, and
 * then defines a {@code compute} method that somehow uses the control
 * methods supplied by this base class.
 *
 * <p>Method {@link #join} and its variants are appropriate for use
 * only when completion dependencies are acyclic; that is, the
 * parallel computation can be described as a directed acyclic graph
 * (DAG). Otherwise, executions may encounter a form of deadlock as
 * tasks cyclically wait for each other.  However, this framework
 * supports other methods and techniques (for example the use of
 * {@link Phaser}, {@link #helpQuiesce}, and {@link #complete}) that
 * may be of use in constructing custom subclasses for problems that
 * are not statically structured as DAGs. To support such usages, a
 * ForkJoinTask may be atomically <em>tagged</em> with a {@code short}
 * value using {@link #setForkJoinTaskTag} or {@link
 * #compareAndSetForkJoinTaskTag} and checked using {@link
 * #getForkJoinTaskTag}. The ForkJoinTask implementation does not use
 * these {@code protected} methods or tags for any purpose, but they
 * may be of use in the construction of specialized subclasses.  For
 * example, parallel graph traversals can use the supplied methods to
 * avoid revisiting nodes/tasks that have already been processed.
 * (Method names for tagging are bulky in part to encourage definition
 * of methods that reflect their usage patterns.)
 *
 * <p>Most base support methods are {@code final}, to prevent
 * overriding of implementations that are intrinsically tied to the
 * underlying lightweight task scheduling framework.  Developers
 * creating new basic styles of fork/join processing should minimally
 * implement {@code protected} methods {@link #exec}, {@link
 * #setRawResult}, and {@link #getRawResult}, while also introducing
 * an abstract computational method that can be implemented in its
 * subclasses, possibly relying on other {@code protected} methods
 * provided by this class.
 *
 * <p>ForkJoinTasks should perform relatively small amounts of
 * computation. Large tasks should be split into smaller subtasks,
 * usually via recursive decomposition. As a very rough rule of thumb,
 * a task should perform more than 100 and less than 10000 basic
 * computational steps, and should avoid indefinite looping. If tasks
 * are too big, then parallelism cannot improve throughput. If too
 * small, then memory and internal task maintenance overhead may
 * overwhelm processing.
 *
 * <p>This class provides {@code adapt} methods for {@link Runnable}
 * and {@link Callable}, that may be of use when mixing execution of
 * {@code ForkJoinTasks} with other kinds of tasks. When all tasks are
 * {@code ForkJoinTasks} with other kinds of tasks. When all tasks are
 * of this form, consider using a pool constructed in <em>asyncMode</em>.
 *
 * <p>ForkJoinTasks are {@code Serializable}, which enables them to be
 * used in extensions such as remote execution frameworks. It is
 * sensible to serialize tasks only before or after, but not during,
 * execution. Serialization is not relied on during execution itself.
 *
 * @since 1.7
 * @author Doug Lea
 */
//ForkJoinTask实现了future,也可以序列化,但它不是一个Runnable或Callable.
public abstract class ForkJoinTask<V> implements Future<V>, Serializable {

    /*
     * See the internal documentation of class ForkJoinPool for a
     * general implementation overview.  ForkJoinTasks are mainly
     * responsible for maintaining their "status" field amidst relays
     * to methods in ForkJoinWorkerThread and ForkJoinPool.
     *
     * The methods of this class are more-or-less layered into
     * (1) basic status maintenance
     * (2) execution and awaiting completion
     * (3) user-level methods that additionally report results.
     * This is sometimes hard to see because this file orders exported
     * methods in a way that flows well in javadocs.
     */

    /*
     * The status field holds run control status bits packed into a
     * single int to minimize footprint and to ensure atomicity (via
     * CAS).  Status is initially zero, and takes on nonnegative
     * values until completed, upon which status (anded with
     * DONE_MASK) holds value NORMAL, CANCELLED, or EXCEPTIONAL. Tasks
     * undergoing blocking waits by other threads have the SIGNAL bit
     * set.  Completion of a stolen task with SIGNAL set awakens any
     * waiters via notifyAll. Even though suboptimal for some
     * purposes, we use basic builtin wait/notify to take advantage of
     * "monitor inflation" in JVMs that we would otherwise need to
     * emulate to avoid adding further per-task bookkeeping overhead.
     * We want these monitors to be "fat", i.e., not use biasing or
     * thin-lock techniques, so use some odd coding idioms that tend
     * to avoid them, mainly by arranging that every synchronized
     * block performs a wait, notifyAll or both.
     *
     * These control bits occupy only (some of) the upper half (16
     * bits) of status field. The lower bits are used for user-defined
     * tags.
     */

    /** The run status of this task */
    /**
     *  一、 任务运行状态
     *      1、DONE_MASK最高4位为1，可以通过与运算屏蔽除后4位的所有位。
     *      2、最终状态NORMAL，CANCELLED,EXCEPTIONAL状态均为负数，即最高4位不会为0。而剩下的SIGNAL和SMASK高32均为0
     *      3、0xf = 1111   0xc = 1100    0x8 = 1000，可能通过一个状态什s，通过和DONE_MASK进行与位运算，得到NORMAL，EXCEPTIONAL，CANCELLED三种状态之一
     *      4、DONE_MASK通过与位运算，能够过滤掉所有非NORMAL,非CANCELLED,非EXCEPTIONAL的状态
     * */

    volatile int status; // accessed directly by pool and workers // 任务运行状态
    /** DONE_MASK = 0b11110000_00000000_00000000_00000000，高4位为1，可以过滤其它标识位，用来判断状态*/
    static final int DONE_MASK   = 0xf0000000;  // mask out non-completion bits // （遮掩未完成的标识）任务完成状态标志位
    /** NORMAL = 0b11110000_00000000_00000000_00000000*/
    static final int NORMAL      = 0xf0000000;  // must be negative     //正常状态，必须为负数
    /** CANCELLED = 0b11010000_00000000_00000000_00000000*/
    static final int CANCELLED   = 0xc0000000;  // must be < NORMAL     //取消状态，是负值.必须小于NORMAL
    /** CANCELLED = 0b10000000_00000000_00000000_00000000*/
    static final int EXCEPTIONAL = 0x80000000;  // must be < CANCELLED  //异常状态,负值，必须小于CANCELLED
    /** SIGNAL = 0b00000000_00000001_00000000_00000000*/
    static final int SIGNAL      = 0x00010000;  // must be >= 1 << 16  等待信号,必须不小于1<<16,默认为1<<16.
    /** SIGNAL = 0b00000000_00000001_11111111_11111111*/
    static final int SMASK       = 0x0000ffff;  // short bits for tags  低位掩码,后十六位的task标签

    /**
     * Marks completion and wakes up threads waiting to join this
     * task.
     * 1、标记当前task的completion状态,同时根据情况唤醒等待该task的线程.
     * 2、如果传入的参数不能将status设置为负值会如何?
     *      显然,可能会有至多一次的成功cas,并且若满足唤醒的条件,会尝试去唤醒线程,甚至可能因为为了唤醒其他线程而被
     *      阻塞在synchonized代码块外;也可能没有一次成功的cas,直到其他线程成功将status置为完成。
     * @param completion one of NORMAL, CANCELLED, EXCEPTIONAL
     * @return completion status on exit
     */
    private int setCompletion(int completion) {
        for (int s;;) {
            //开启一个循环,如果当前task的status已经是完成(小于0,包括NORMAL，CANCELLED,EXCEPTIONAL),则直接返回status,这
            //个status可能是某一次循环前被其他线程完成.
            if ((s = status) < 0)
                return s;

            //尝试将原来的status设置为它与completion按位或的结果.
            if (U.compareAndSwapInt(this, STATUS, s, s | completion)) {
                if ((s >>> 16) != 0)//若条件成立，表明S的高16位不全为0，可以为负数(最高痊32位为1或者其它)，即状态是已经完成状态
                    //此处体现了SIGNAL的标记作用,很明显,只要task完成(包含取消或异常),或completion传入的值不小于1<<16,
                    //就可以起到唤醒其他线程的作用.
                    synchronized (this) { notifyAll(); }

                //cas成功,返回参数中的completion.
                return completion;
            }
        }
    }

    /**
     * Primary execution method for stolen tasks. Unless done, calls
     * exec and records status if completed, but doesn't wait for
     * completion otherwise.
     * final修饰,运行ForkJoinTask的核心方法.
     * @return status on exit from this method
     */
    final int doExec() {
        int s; boolean completed;
        //仅未完成的任务会运行,其他情况会忽略.
        if ((s = status) >= 0) {
            try {
                //调用exec
                completed = exec();
            } catch (Throwable rex) {
                //发生异常,用setExceptionalCompletion设置结果
                return setExceptionalCompletion(rex);
            }
            if (completed)
                //正常完成,调用前面说过的setCompletion,参数为normal,并将返回值作为结果s.
                s = setCompletion(NORMAL);
        }
        //返回s
        return s;
    }

    /**
     * If not done, sets SIGNAL status and performs Object.wait(timeout).
     * This task may or may not be done on exit. Ignores interrupts.
     * 内部等待任务完成,直到完成或超时.
     * 1、首先判断是否未完成,满足未完成,则将标记位加上SIGNAL(可能已有别的线程做过)
     * 2、随后加锁double check status,还未完成则等待并释放锁,若发现已完成,或在后续被唤醒后发现已完成,则唤醒其他等待线程.
     * 3、通过notifyAll的方式避免了通知丢失.
     * 4、此方法在ForkJoinPool::awaitJoin中被调用
     * @param timeout using Object.wait conventions.
     */
    final void internalWait(long timeout) {
        int s;
        //status小于0代表已完成,直接忽略wait.
        //未完成,则试着加上SIGNAL的标记,令完成任务的线程唤醒这个等待.
        if ((s = status) >= 0 && // force completer to issue notify 强制完成程序发出通知
            U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
            //加锁,只有一个线程可以进入.
            synchronized (this) {
                //再次判断未完成.等待timeout,且忽略扰动异常.
                if (status >= 0)
                    try { wait(timeout); } catch (InterruptedException ie) { }
                else
                    //已完成则响醒其他等待者.
                    notifyAll();
            }
        }
    }

    /**
     * Blocks a non-worker-thread until completion.
     * ForkJoin框架的一个核心:外部帮助.外部线程等待一个common池中的任务完成.
     * @return status upon completion
     * 1、在当前task为ForkJoinPool.common的情况下可以在外部进行等待和尝试帮助完成
     * 2、方法会首先根据ForkJoinTask的类型进行尝试帮助,并返回当前的status,
     * 3、若发现未完成,则进入下面的等待唤醒逻辑.该方法的调用者为非worker线程.
     */
    private int externalAwaitDone() {
        int s = ((this instanceof CountedCompleter) ? // try helping
                //当前task是一个CountedCompleter,尝试使用common ForkJoinPool去外部帮助完成,并将完成状态返回.
                ForkJoinPool.common.externalHelpComplete(
                     (CountedCompleter<?>)this, 0) :
                //当前task不是CountedCompleter,则调用common pool尝试外部弹出该任务并进行执行,
                //status赋值doExec函数的结果,若弹出失败(其他线程先行弹出)赋0.
                 ForkJoinPool.common.tryExternalUnpush(this) ? doExec() : 0);
        if (s >= 0 && (s = status) >= 0) {
            //检查上一步的结果,即外部使用common池弹出并执行的结果(不是CountedCompleter的情况),或外部尝试帮助CountedCompleter完成的结果
            //status大于0表示尝试帮助完成失败.
            //扰动标识,初值false
            boolean interrupted = false;
            do {
                //循环尝试,先给status标记SIGNAL标识,便于后续唤醒操作.
                if (U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
                    synchronized (this) {
                        if (status >= 0) {
                            try {
                                //CAS成功,进同步块发现double check未完成,则等待.
                                wait(0L);
                            } catch (InterruptedException ie) {
                                //若在等待过程中发生了扰动,不停止等待,标记扰动.
                                interrupted = true;
                            }
                        }
                        else
                            //进同步块发现已完成,则唤醒所有等待线程.
                            notifyAll();
                    }
                }
            } while ((s = status) >= 0); //循环条件,task未完成.
            if (interrupted)
                //循环结束,若循环中间曾有扰动,则中断当前线程.
                Thread.currentThread().interrupt();
        }
        return s;
    }

    /**
     * Blocks a non-worker-thread until completion or interruption.
     * 1、externalInterruptibleAwaitDone的逻辑与externalAwaitDone相似,只是对中断异常的态度为抛,后者为catch.
     * 2、它们的使用点,externalAwaitDone为doJoin或doInvoke方法调用,externalInterruptibleAwaitDone为get方法调用,
     *  很明显,join操作不可扰动,get则可以扰动.
     */
    private int externalInterruptibleAwaitDone() throws InterruptedException {
        int s;
        //不同于externalAwaitDone,入口处发现当前线程已中断,则立即抛出中断异常.
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((s = status) >= 0 &&
            (s = ((this instanceof CountedCompleter) ?
                  ForkJoinPool.common.externalHelpComplete(
                      (CountedCompleter<?>)this, 0) :
                  ForkJoinPool.common.tryExternalUnpush(this) ? doExec() :
                  0)) >= 0) {
            while ((s = status) >= 0) {
                if (U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
                    synchronized (this) {
                        if (status >= 0)
                            //wait时也不catch中断异常,发生即抛出.
                            wait(0L);
                        else
                            notifyAll();
                    }
                }
            }
        }
        return s;
    }

    /**
     * Implementation for join, get, quietlyJoin. Directly handles
     * only cases of already-completed, external wait, and
     * unfork+exec.  Others are relayed to ForkJoinPool.awaitJoin.
     * join的核心方法
     * @return status upon completion
     */
    private int doJoin() {
        int s; Thread t; ForkJoinWorkerThread wt; ForkJoinPool.WorkQueue w;
        //已完成,返回status,未完成再尝试后续
        return (s = status) < 0 ? s :
                //未完成,当前线程是ForkJoinWorkerThread,从该线程中取出workQueue,并尝试将
                //当前task出队然后执行,执行的结果是完成则返回状态,否则使用当线程池所在的ForkJoinPool的awaitJoin方法等待.
            ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
            (w = (wt = (ForkJoinWorkerThread)t).workQueue).
            tryUnpush(this) && (s = doExec()) < 0 ? s :
            wt.pool.awaitJoin(w, this, 0L) :
            //当前线程不是ForkJoinWorkerThread,调用前面说的externalAwaitDone方法.
            externalAwaitDone();
    }

    /**
     * Implementation for invoke, quietlyInvoke.
     * invoke的核心方法
     * @return status upon completion
     */
    private int doInvoke() {
        int s; Thread t; ForkJoinWorkerThread wt;
        //先尝试本线程执行,不成功才走后续流程
        return (s = doExec()) < 0 ? s :
            ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
             //与上一个方法基本相同,但在当前线程是ForkJoinWorkerThread时不尝试将该task移除栈并执行,而是等
            (wt = (ForkJoinWorkerThread)t).pool.
            awaitJoin(wt.workQueue, this, 0L) :
            externalAwaitDone();
    }

    // Exception table support

    /**
     * Table of exceptions thrown by tasks, to enable reporting by
     * callers. Because exceptions are rare, we don't directly keep
     * them with task objects, but instead use a weak ref table.  Note
     * that cancellation exceptions don't appear in the table, but are
     * instead recorded as status values.
     * 用来记录所有子任务的异常信息
     * Note: These statics are initialized below in static block.
     */
    //exceptionTable声明
    private static final ExceptionNode[] exceptionTable; //全局异常node表
    private static final ReentrantLock exceptionTableLock;//异常锁,就是一个普通的可重入锁.
    private static final ReferenceQueue<Object> exceptionTableRefQueue; ;//变量表引用队列

    /**
     * Fixed capacity for exceptionTable.
     *  异常表的固定容量,只有32而且是全局的.
     */
    private static final int EXCEPTION_MAP_CAPACITY = 32;

    /**
     * Key-value nodes for exception table.  The chained hash table
     * uses identity comparisons, full locking, and weak references
     * for keys. The table has a fixed capacity because it only
     * maintains task exceptions long enough for joiners to access
     * them, so should never become very large for sustained
     * periods. However, since we do not know when the last joiner
     * completes, we must use weak references and expunge them. We do
     * so on each operation (hence full locking). Also, some thread in
     * any ForkJoinPool will call helpExpungeStaleExceptions when its
     * pool becomes isQuiescent.
     */
    //签名,实现了一个ForkJoinTask的弱引用.
    static final class ExceptionNode extends WeakReference<ForkJoinTask<?>> {
        final Throwable ex;
        ExceptionNode next;
        final long thrower;  // use id not ref to avoid weak cycles
        final int hashCode;  // store task hashCode before weak ref disappears
        ExceptionNode(ForkJoinTask<?> task, Throwable ex, ExceptionNode next) {
            super(task, exceptionTableRefQueue);//指向弱引用的构造函数,保存引用为task,队列为全局的exceptionTableRefQueue.
            this.ex = ex;//抛出的异常的引用
            this.next = next;//数组中的ExceptionNode以链表形式存在,前面分析过,先入者为后入者的next
            this.thrower = Thread.currentThread().getId();//保存抛出异常的线程id(严格来说是创建了this的线程)
            this.hashCode = System.identityHashCode(task);//哈希码保存关联task的哈希值.
        }
    }

    /**
     * Records exception and sets status.
     * 记录异常完成
     * @return status on exit
     */
    final int recordExceptionalCompletion(Throwable ex) {
        int s;
        if ((s = status) >= 0) {
            //只能是异常态的status可以记录.
            //hash值禁止重写,不使用子类的hashcode函数.
            int h = System.identityHashCode(this);
            final ReentrantLock lock = exceptionTableLock;
            //异常锁,加锁
            lock.lock();
            try {
                //抹除脏异常,后面叙述
                expungeStaleExceptions();
                //异常表数组.ExceptionNode
                ExceptionNode[] t = exceptionTable;//exceptionTable是一个全局的静态常量
                int i = h & (t.length - 1);
                for (ExceptionNode e = t[i]; ; e = e.next) {
                    //找到空的索引位,就创建一个新的ExceptionNode,保存this,异常对象并退出循环
                    if (e == null) {
                        t[i] = new ExceptionNode(this, ex, t[i]);
                        break;
                    }
                    if (e.get() == this) // already present//已设置在相同的索引位置的链表中,退出循环.//2
                        break;
                    //否则e指向t[i]的next,进入下个循环,直到发现判断包装this这个ForkJoinTask的ExceptionNode已经出现在t[i]这个链表并break(2),
                    //或者直到e是null,意味着t[i]出发开始的链表并无包装this的ExceptionNode,则将构建一个新的ExceptionNode并置换t[i],
                    //将原t[i]置为它的next(1).整个遍历判断和置换过程处在锁中进行.
                }
            } finally {
                lock.unlock();
            }
            //记录成功,将当前task设置为异常完成.
            s = setCompletion(EXCEPTIONAL);
        }
        return s;
    }

    /**
     * Records exception and possibly propagates.
     * 记录异常并且在符合条件时传播异常行为
     * 1、在产生异常时尝试将异常存放在全局的execptionTable中,存放的结构为数组+链表,按哈希值指定索引,
     * 每次存放新的异常时,顺便清理上一次已被gc回收的ExceptionNode.所有ForkJoinTask共享了一个exceptionTable,
     * 因此必然在有关的几个环节要进行及时的清理.除了刚刚论述的过程
     * @return status on exit
     */
    private int setExceptionalCompletion(Throwable ex) {
        //首先记录异常信息到结果
        int s = recordExceptionalCompletion(ex);
        if ((s & DONE_MASK) == EXCEPTIONAL)
            //status去除非完成态标志位(只保留前4位),等于EXCEPTIONAL.内部传播异常
            internalPropagateException(ex);
        return s;
    }

    /**
     * Hook for exception propagation support for tasks with completers.
     * 构子方法，internalPropagateException方法是一个空方法,留给子类实现,可用于completer之间的异常传递
     */
    void internalPropagateException(Throwable ex) {
    }

    /**
     * Cancels, ignoring any exceptions thrown by cancel. Used during
     * worker and pool shutdown. Cancel is spec'ed not to throw any
     * exceptions, but if it does anyway, we have no recourse during
     * shutdown, so guard against this case.
     */
    static final void cancelIgnoringExceptions(ForkJoinTask<?> t) {
        if (t != null && t.status >= 0) {
            try {
                t.cancel(false);
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * Removes exception node and clears status.
     */
    private void clearExceptionalCompletion() {
        int h = System.identityHashCode(this);
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            ExceptionNode[] t = exceptionTable;
            int i = h & (t.length - 1);
            ExceptionNode e = t[i];
            ExceptionNode pred = null;
            while (e != null) {
                ExceptionNode next = e.next;
                if (e.get() == this) {
                    if (pred == null)
                        t[i] = next;
                    else
                        pred.next = next;
                    break;
                }
                pred = e;
                e = next;
            }
            expungeStaleExceptions();
            status = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a rethrowable exception for the given task, if
     * available. To provide accurate stack traces, if the exception
     * was not thrown by the current thread, we try to create a new
     * exception of the same type as the one thrown, but with the
     * recorded exception as its cause. If there is no such
     * constructor, we instead try to use a no-arg constructor,
     * followed by initCause, to the same effect. If none of these
     * apply, or any fail due to other exceptions, we return the
     * recorded exception, which is still correct, although it may
     * contain a misleading stack trace.
     *
     * @return the exception, or null if none
     */
    private Throwable getThrowableException() {
        if ((status & DONE_MASK) != EXCEPTIONAL)
            return null;
        int h = System.identityHashCode(this);
        ExceptionNode e;
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            expungeStaleExceptions();
            ExceptionNode[] t = exceptionTable;
            e = t[h & (t.length - 1)];
            while (e != null && e.get() != this)
                e = e.next;
        } finally {
            lock.unlock();
        }
        Throwable ex;
        if (e == null || (ex = e.ex) == null)
            return null;
        if (e.thrower != Thread.currentThread().getId()) {
            Class<? extends Throwable> ec = ex.getClass();
            try {
                Constructor<?> noArgCtor = null;
                Constructor<?>[] cs = ec.getConstructors();// public ctors only
                for (int i = 0; i < cs.length; ++i) {
                    Constructor<?> c = cs[i];
                    Class<?>[] ps = c.getParameterTypes();
                    if (ps.length == 0)
                        noArgCtor = c;
                    else if (ps.length == 1 && ps[0] == Throwable.class) {
                        Throwable wx = (Throwable)c.newInstance(ex);
                        return (wx == null) ? ex : wx;
                    }
                }
                if (noArgCtor != null) {
                    Throwable wx = (Throwable)(noArgCtor.newInstance());
                    if (wx != null) {
                        wx.initCause(ex);
                        return wx;
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return ex;
    }

    /**
     * Poll stale refs and remove them. Call only while holding lock.
     * //清除掉异常表中的脏数据,仅在持有全局锁时才可使用.前面看到在记录新的异常信息时要进行一次清除尝试
     * expunge:擦除，删除
     * stale:脏的，陈旧的，腐蚀的
     */
    private static void expungeStaleExceptions() {
        //循环条件,全局exceptionTableRefQueue队列不为空,前面说过ExceptionNode是弱引用,当它被回收时会被放入此队列.
        for (Object x; (x = exceptionTableRefQueue.poll()) != null;) {
            //从队首依次取出元素.
            if (x instanceof ExceptionNode) {
                //计算在全局exceptionTable中的索引.
                int hashCode = ((ExceptionNode)x).hashCode;
                ExceptionNode[] t = exceptionTable;
                int i = hashCode & (t.length - 1);
                //取出node
                ExceptionNode e = t[i];
                ExceptionNode pred = null;
                //不停遍历,直到e是null为止.
                while (e != null) {
                    //e的next
                    ExceptionNode next = e.next;
                    //x是队首出队的元素.它与e相等说明找到
                    if (e == x) {
                        //e是一个链表的元素,pred表示它是否有前置元素
                        if (pred == null)
                            //无前置元素,说明e在链表首部,直接将首部元素指向next即可.
                            t[i] = next;
                        else
                            //有前置元素,说明循环过若干次,将当前e出链表
                            pred.next = next;
                            //在链表中发现x即break掉内循环,继续从exceptionTableRefQueue的队首弹出新的元素.
                        break;
                    }
                    //只要发现当前e不是x,准备下一次循环,pred指向e.e指向next,进行下一个元素的比较.
                    pred = e;
                    e = next;
                }
            }
        }
    }

    /**
     * If lock is available, poll stale refs and remove them.
     * Called from ForkJoinPool when pools become quiescent.
     */
    static final void helpExpungeStaleExceptions() {
        final ReentrantLock lock = exceptionTableLock;
        if (lock.tryLock()) {
            try {
                expungeStaleExceptions();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * A version of "sneaky throw" to relay exceptions
     */
    static void rethrow(Throwable ex) {
        if (ex != null)
            ForkJoinTask.<RuntimeException>uncheckedThrow(ex);
    }

    /**
     * The sneaky part of sneaky throw, relying on generics
     * limitations to evade compiler complaints about rethrowing
     * unchecked exceptions
     */
    @SuppressWarnings("unchecked") static <T extends Throwable>
        void uncheckedThrow(Throwable t) throws T {
        throw (T)t; // rely on vacuous cast
    }

    /**
     * Throws exception, if any, associated with the given status.
     * 抛出异常,可在前一步判断执行status是否为异常态,然后获取并重抛异常.
     */
    private void reportException(int s) {
        if (s == CANCELLED)
            throw new CancellationException();
        if (s == EXCEPTIONAL)
            rethrow(getThrowableException());
    }

    // public methods

    /**
     * Arranges to asynchronously execute this task in the pool the
     * current task is running in, if applicable, or using the {@link
     * ForkJoinPool#commonPool()} if not {@link #inForkJoinPool}.  While
     * it is not necessarily enforced, it is a usage error to fork a
     * task more than once unless it has completed and been
     * reinitialized.  Subsequent modifications to the state of this
     * task or any data it operates on are not necessarily
     * consistently observable by any thread other than the one
     * executing it unless preceded by a call to {@link #join} or
     * related methods, or a call to {@link #isDone} returning {@code
     * true}.
     *
     * @return {@code this}, to simplify usage
     * 对任务进行拆分
     */
    public final ForkJoinTask<V> fork() {
        Thread t;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            ((ForkJoinWorkerThread)t).workQueue.push(this);
        else
            ForkJoinPool.common.externalPush(this);
        return this;
    }

    /**
     * Returns the result of the computation when it {@link #isDone is
     * done}.  This method differs from {@link #get()} in that
     * abnormal completion results in {@code RuntimeException} or
     * {@code Error}, not {@code ExecutionException}, and that
     * interrupts of the calling thread do <em>not</em> cause the
     * method to abruptly return by throwing {@code
     * InterruptedException}.
     *
     * @return the computed result
     * 等待子任务完成
     */
    public final V join() {
        int s;
        if ((s = doJoin()//核心方法入口
                & DONE_MASK) != NORMAL)
            reportException(s);
        return getRawResult();
    }

    /**
     * Commences performing this task, awaits its completion if
     * necessary, and returns its result, or throws an (unchecked)
     * {@code RuntimeException} or {@code Error} if the underlying
     * computation did so.
     * invoke公有方法.
     * @return the computed result
     */
    public final V invoke() {
        int s;
        //先尝试执行
        if ((s = doInvoke() & DONE_MASK) != NORMAL)
            //doInvoke方法的结果status只保留完成态位表示非NORMAL,则报告异常.
            reportException(s);
        //正常完成,返回原始结果.
        return getRawResult();
    }

    /**
     * Forks the given tasks, returning when {@code isDone} holds for
     * each task or an (unchecked) exception is encountered, in which
     * case the exception is rethrown. If more than one task
     * encounters an exception, then this method throws any one of
     * these exceptions. If any task encounters an exception, the
     * other may be cancelled. However, the execution status of
     * individual tasks is not guaranteed upon exceptional return. The
     * status of each task may be obtained using {@link
     * #getException()} and related methods to check if they have been
     * cancelled, completed normally or exceptionally, or left
     * unprocessed.
     *
     * @param t1 the first task
     * @param t2 the second task
     * @throws NullPointerException if any task is null
     * 1、在compute()中拆分任务时，应该调用此方法，调用此方法不会浪费当前线程。
     *
     * 2、通常错误的写法：
     * protected Long compute() {
     *     if (任务足够小?) {
     *         return computeDirect();
     *     }
     *     // 任务太大,一分为二:
     *     SumTask subtask1 = new SumTask(...);
     *     SumTask subtask2 = new SumTask(...);
     *     // 分别对子任务调用fork():
     *     subtask1.fork();
     *     subtask2.fork();
     *     // 合并结果:
     *     Long subresult1 = subtask1.join();
     *     Long subresult2 = subtask2.join();
     *     return subresult1 + subresult2;
     * }
     *
     * 3、正确的写法
     *      * protected Long compute() {
     *      *     if (任务足够小?) {
     *      *         return computeDirect();
     *      *     }
     *      *     // 任务太大,一分为二:
     *      *     SumTask subtask1 = new SumTask(...);
     *      *     SumTask subtask2 = new SumTask(...);
     *            //
     *      *     invokeAll(subtask1, subtask2);
     *      *     // 合并结果:
     *      *     Long subresult1 = subtask1.join();
     *      *     Long subresult2 = subtask2.join();
     *      *     return subresult1 + subresult2;
     *      * }
     *
     */
    public static void invokeAll(ForkJoinTask<?> t1, ForkJoinTask<?> t2) {
        int s1, s2;
        t2.fork();// t2调用Fork()在新线程中执行
        if ((s1 = t1.doInvoke() & DONE_MASK) != NORMAL)//t1直接doInvoke，在当前线程执行，不需要fork。
            t1.reportException(s1);
        if ((s2 = t2.doJoin() & DONE_MASK) != NORMAL)//等待t2结束
            t2.reportException(s2);
    }

    /**
     * Forks the given tasks, returning when {@code isDone} holds for
     * each task or an (unchecked) exception is encountered, in which
     * case the exception is rethrown. If more than one task
     * encounters an exception, then this method throws any one of
     * these exceptions. If any task encounters an exception, others
     * may be cancelled. However, the execution status of individual
     * tasks is not guaranteed upon exceptional return. The status of
     * each task may be obtained using {@link #getException()} and
     * related methods to check if they have been cancelled, completed
     * normally or exceptionally, or left unprocessed.
     *
     * @param tasks the tasks
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(ForkJoinTask<?>... tasks) {
        Throwable ex = null;
        int last = tasks.length - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t = tasks[i];
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            }
            else if (i != 0)
                t.fork();
            else if (t.doInvoke() < NORMAL && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = tasks[i];
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if (t.doJoin() < NORMAL)
                    ex = t.getException();
            }
        }
        if (ex != null)
            rethrow(ex);
    }

    /**
     * Forks all tasks in the specified collection, returning when
     * {@code isDone} holds for each task or an (unchecked) exception
     * is encountered, in which case the exception is rethrown. If
     * more than one task encounters an exception, then this method
     * throws any one of these exceptions. If any task encounters an
     * exception, others may be cancelled. However, the execution
     * status of individual tasks is not guaranteed upon exceptional
     * return. The status of each task may be obtained using {@link
     * #getException()} and related methods to check if they have been
     * cancelled, completed normally or exceptionally, or left
     * unprocessed.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return the tasks argument, to simplify usage
     * @throws NullPointerException if tasks or any element are null
     */
    public static <T extends ForkJoinTask<?>> Collection<T> invokeAll(Collection<T> tasks) {
        if (!(tasks instanceof RandomAccess) || !(tasks instanceof List<?>)) {
            invokeAll(tasks.toArray(new ForkJoinTask<?>[tasks.size()]));
            return tasks;
        }
        @SuppressWarnings("unchecked")
        List<? extends ForkJoinTask<?>> ts =
            (List<? extends ForkJoinTask<?>>) tasks;
        Throwable ex = null;
        int last = ts.size() - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t = ts.get(i);
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            }
            else if (i != 0)
                t.fork();
            else if (t.doInvoke() < NORMAL && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = ts.get(i);
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if (t.doJoin() < NORMAL)
                    ex = t.getException();
            }
        }
        if (ex != null)
            rethrow(ex);
        return tasks;
    }

    /**
     * Attempts to cancel execution of this task. This attempt will
     * fail if the task has already completed or could not be
     * cancelled for some other reason. If successful, and this task
     * has not started when {@code cancel} is called, execution of
     * this task is suppressed. After this method returns
     * successfully, unless there is an intervening call to {@link
     * #reinitialize}, subsequent calls to {@link #isCancelled},
     * {@link #isDone}, and {@code cancel} will return {@code true}
     * and calls to {@link #join} and related methods will result in
     * {@code CancellationException}.
     *
     * <p>This method may be overridden in subclasses, but if so, must
     * still ensure that these properties hold. In particular, the
     * {@code cancel} method itself must not throw exceptions.
     *
     * <p>This method is designed to be invoked by <em>other</em>
     * tasks. To terminate the current task, you can just return or
     * throw an unchecked exception from its computation method, or
     * invoke {@link #completeExceptionally(Throwable)}.
     *
     * @param mayInterruptIfRunning this value has no effect in the
     * default implementation because interrupts are not used to
     * control cancellation.
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return (setCompletion(CANCELLED) & DONE_MASK) == CANCELLED;
    }

    public final boolean isDone() {
        return status < 0;
    }

    public final boolean isCancelled() {
        return (status & DONE_MASK) == CANCELLED;
    }

    /**
     * Returns {@code true} if this task threw an exception or was cancelled.
     *
     * @return {@code true} if this task threw an exception or was cancelled
     */
    public final boolean isCompletedAbnormally() {
        return status < NORMAL;
    }

    /**
     * Returns {@code true} if this task completed without throwing an
     * exception and was not cancelled.
     *
     * @return {@code true} if this task completed without throwing an
     * exception and was not cancelled
     */
    public final boolean isCompletedNormally() {
        return (status & DONE_MASK) == NORMAL;
    }

    /**
     * Returns the exception thrown by the base computation, or a
     * {@code CancellationException} if cancelled, or {@code null} if
     * none or if the method has not yet completed.
     *
     * @return the exception, or {@code null} if none
     */
    public final Throwable getException() {
        int s = status & DONE_MASK;
        return ((s >= NORMAL)    ? null :
                (s == CANCELLED) ? new CancellationException() :
                getThrowableException());
    }

    /**
     * Completes this task abnormally, and if not already aborted or
     * cancelled, causes it to throw the given exception upon
     * {@code join} and related operations. This method may be used
     * to induce exceptions in asynchronous tasks, or to force
     * completion of tasks that would not otherwise complete.  Its use
     * in other situations is discouraged.  This method is
     * overridable, but overridden versions must invoke {@code super}
     * implementation to maintain guarantees.
     *
     * @param ex the exception to throw. If this exception is not a
     * {@code RuntimeException} or {@code Error}, the actual exception
     * thrown will be a {@code RuntimeException} with cause {@code ex}.
     */
    public void completeExceptionally(Throwable ex) {
        setExceptionalCompletion((ex instanceof RuntimeException) ||
                                 (ex instanceof Error) ? ex :
                                 new RuntimeException(ex));
    }

    /**
     * Completes this task, and if not already aborted or cancelled,
     * returning the given value as the result of subsequent
     * invocations of {@code join} and related operations. This method
     * may be used to provide results for asynchronous tasks, or to
     * provide alternative handling for tasks that would not otherwise
     * complete normally. Its use in other situations is
     * discouraged. This method is overridable, but overridden
     * versions must invoke {@code super} implementation to maintain
     * guarantees.
     *
     * @param value the result value for this task
     */
    public void complete(V value) {
        try {
            setRawResult(value);
        } catch (Throwable rex) {
            setExceptionalCompletion(rex);
            return;
        }
        setCompletion(NORMAL);
    }

    /**
     * Completes this task normally without setting a value. The most
     * recent value established by {@link #setRawResult} (or {@code
     * null} by default) will be returned as the result of subsequent
     * invocations of {@code join} and related operations.
     *
     * @since 1.8
     */
    public final void quietlyComplete() {
        setCompletion(NORMAL);
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread is not a
     * member of a ForkJoinPool and was interrupted while waiting
     * 公有对外方法
     */
    //get方法还有get(long time)的变种.
    public final V get() throws InterruptedException, ExecutionException {
        //当前线程是ForkJoinWorkerThread则调用前面提过的doJoin方法.
        //否则调用前述externalInterruptibleAwaitDone
        int s = (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            doJoin() : externalInterruptibleAwaitDone();
        Throwable ex;
        if ((s &= DONE_MASK) == CANCELLED)
            //异常处理,取消的任务,抛出CancellationException.
            throw new CancellationException();
        if (s == EXCEPTIONAL && (ex = getThrowableException()) != null)
            //异常处理,调用getThrowableException获取异常,封进ExecutionException.
            throw new ExecutionException(ex);
        //无异常处理,返回原始结果.
        return getRawResult();
    }

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread is not a
     * member of a ForkJoinPool and was interrupted while waiting
     * @throws TimeoutException if the wait timed out
     */
    public final V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        int s;
        long nanos = unit.toNanos(timeout);
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((s = status) >= 0 && nanos > 0L) {
            long d = System.nanoTime() + nanos;
            long deadline = (d == 0L) ? 1L : d; // avoid 0
            Thread t = Thread.currentThread();
            if (t instanceof ForkJoinWorkerThread) {
                ForkJoinWorkerThread wt = (ForkJoinWorkerThread)t;
                s = wt.pool.awaitJoin(wt.workQueue, this, deadline);
            }
            else if ((s = ((this instanceof CountedCompleter) ?
                           ForkJoinPool.common.externalHelpComplete(
                               (CountedCompleter<?>)this, 0) :
                           ForkJoinPool.common.tryExternalUnpush(this) ?
                           doExec() : 0)) >= 0) {
                long ns, ms; // measure in nanosecs, but wait in millisecs
                while ((s = status) >= 0 &&
                       (ns = deadline - System.nanoTime()) > 0L) {
                    if ((ms = TimeUnit.NANOSECONDS.toMillis(ns)) > 0L &&
                        U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
                        synchronized (this) {
                            if (status >= 0)
                                wait(ms); // OK to throw InterruptedException
                            else
                                notifyAll();
                        }
                    }
                }
            }
        }
        if (s >= 0)
            s = status;
        if ((s &= DONE_MASK) != NORMAL) {
            Throwable ex;
            if (s == CANCELLED)
                throw new CancellationException();
            if (s != EXCEPTIONAL)
                throw new TimeoutException();
            if ((ex = getThrowableException()) != null)
                throw new ExecutionException(ex);
        }
        return getRawResult();
    }

    /**
     * Joins this task, without returning its result or throwing its
     * exception. This method may be useful when processing
     * collections of tasks when some have been cancelled or otherwise
     * known to have aborted.
     */
    public final void quietlyJoin() {
        doJoin();
    }

    /**
     * Commences performing this task and awaits its completion if
     * necessary, without returning its result or throwing its
     * exception.
     */
    public final void quietlyInvoke() {
        doInvoke();
    }

    /**
     * Possibly executes tasks until the pool hosting the current task
     * {@link ForkJoinPool#isQuiescent is quiescent}. This method may
     * be of use in designs in which many tasks are forked, but none
     * are explicitly joined, instead executing them until all are
     * processed.
     */
    public static void helpQuiesce() {
        Thread t;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            ForkJoinWorkerThread wt = (ForkJoinWorkerThread)t;
            wt.pool.helpQuiescePool(wt.workQueue);
        }
        else
            ForkJoinPool.quiesceCommonPool();
    }

    /**
     * Resets the internal bookkeeping state of this task, allowing a
     * subsequent {@code fork}. This method allows repeated reuse of
     * this task, but only if reuse occurs when this task has either
     * never been forked, or has been forked, then completed and all
     * outstanding joins of this task have also completed. Effects
     * under any other usage conditions are not guaranteed.
     * This method may be useful when executing
     * pre-constructed trees of subtasks in loops.
     *
     * <p>Upon completion of this method, {@code isDone()} reports
     * {@code false}, and {@code getException()} reports {@code
     * null}. However, the value returned by {@code getRawResult} is
     * unaffected. To clear this value, you can invoke {@code
     * setRawResult(null)}.
     */
    public void reinitialize() {
        if ((status & DONE_MASK) == EXCEPTIONAL)
            clearExceptionalCompletion();
        else
            status = 0;
    }

    /**
     * Returns the pool hosting the current task execution, or null
     * if this task is executing outside of any ForkJoinPool.
     *
     * @see #inForkJoinPool
     * @return the pool, or {@code null} if none
     */
    public static ForkJoinPool getPool() {
        Thread t = Thread.currentThread();
        return (t instanceof ForkJoinWorkerThread) ?
            ((ForkJoinWorkerThread) t).pool : null;
    }

    /**
     * Returns {@code true} if the current thread is a {@link
     * ForkJoinWorkerThread} executing as a ForkJoinPool computation.
     *
     * @return {@code true} if the current thread is a {@link
     * ForkJoinWorkerThread} executing as a ForkJoinPool computation,
     * or {@code false} otherwise
     */
    public static boolean inForkJoinPool() {
        return Thread.currentThread() instanceof ForkJoinWorkerThread;
    }

    /**
     * Tries to unschedule this task for execution. This method will
     * typically (but is not guaranteed to) succeed if this task is
     * the most recently forked task by the current thread, and has
     * not commenced executing in another thread.  This method may be
     * useful when arranging alternative local processing of tasks
     * that could have been, but were not, stolen.
     *
     * @return {@code true} if unforked
     */
    public boolean tryUnfork() {
        Thread t;
        return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread)t).workQueue.tryUnpush(this) :
                ForkJoinPool.common.tryExternalUnpush(this));
    }

    /**
     * Returns an estimate of the number of tasks that have been
     * forked by the current worker thread but not yet executed. This
     * value may be useful for heuristic decisions about whether to
     * fork other tasks.
     *
     * @return the number of tasks
     */
    public static int getQueuedTaskCount() {
        Thread t; ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread)t).workQueue;
        else
            q = ForkJoinPool.commonSubmitterQueue();
        return (q == null) ? 0 : q.queueSize();
    }

    /**
     * Returns an estimate of how many more locally queued tasks are
     * held by the current worker thread than there are other worker
     * threads that might steal them, or zero if this thread is not
     * operating in a ForkJoinPool. This value may be useful for
     * heuristic decisions about whether to fork other tasks. In many
     * usages of ForkJoinTasks, at steady state, each worker should
     * aim to maintain a small constant surplus (for example, 3) of
     * tasks, and to process computations locally if this threshold is
     * exceeded.
     *
     * @return the surplus number of tasks, which may be negative
     */
    public static int getSurplusQueuedTaskCount() {
        return ForkJoinPool.getSurplusQueuedTaskCount();
    }

    // Extension methods

    /**
     * Returns the result that would be returned by {@link #join}, even
     * if this task completed abnormally, or {@code null} if this task
     * is not known to have been completed.  This method is designed
     * to aid debugging, as well as to support extensions. Its use in
     * any other context is discouraged.
     * getRawResult默认为一个抽象实现,在ForkJoinTask中,并未保存该结果的字段.
     * @return the result, or {@code null} if not completed
     */
    public abstract V getRawResult();

    /**
     * Forces the given value to be returned as a result.  This method
     * is designed to support extensions, and should not in general be
     * called otherwise.
     *
     * @param value the value
     */
    protected abstract void setRawResult(V value);

    /**
     * Immediately performs the base action of this task and returns
     * true if, upon return from this method, this task is guaranteed
     * to have completed normally. This method may return false
     * otherwise, to indicate that this task is not necessarily
     * complete (or is not known to be complete), for example in
     * asynchronous actions that require explicit invocations of
     * completion methods. This method may also throw an (unchecked)
     * exception to indicate abnormal exit. This method is designed to
     * support extensions, and should not in general be called
     * otherwise.
     *
     * @return {@code true} if this task is known to have completed normally
     */
    protected abstract boolean exec();

    /**
     * Returns, but does not unschedule or execute, a task queued by
     * the current thread but not yet executed, if one is immediately
     * available. There is no guarantee that this task will actually
     * be polled or executed next. Conversely, this method may return
     * null even if a task exists but cannot be accessed without
     * contention with other threads.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> peekNextLocalTask() {
        Thread t; ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread)t).workQueue;
        else
            q = ForkJoinPool.commonSubmitterQueue();
        return (q == null) ? null : q.peek();
    }

    /**
     * Unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if the
     * current thread is operating in a ForkJoinPool.  This method is
     * designed primarily to support extensions, and is unlikely to be
     * useful otherwise.
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> pollNextLocalTask() {
        Thread t;
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
            ((ForkJoinWorkerThread)t).workQueue.nextLocalTask() :
            null;
    }

    /**
     * If the current thread is operating in a ForkJoinPool,
     * unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if one is
     * available, or if not available, a task that was forked by some
     * other thread, if available. Availability may be transient, so a
     * {@code null} result does not necessarily imply quiescence of
     * the pool this task is operating in.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     *
     * @return a task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> pollTask() {
        Thread t; ForkJoinWorkerThread wt;
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
            (wt = (ForkJoinWorkerThread)t).pool.nextTaskFor(wt.workQueue) :
            null;
    }

    // tag operations

    /**
     * Returns the tag for this task.
     *
     * @return the tag for this task
     * @since 1.8
     */
    public final short getForkJoinTaskTag() {
        return (short)status;
    }

    /**
     * Atomically sets the tag value for this task.
     *
     * @param tag the tag value
     * @return the previous value of the tag
     * @since 1.8
     */
    public final short setForkJoinTaskTag(short tag) {
        for (int s;;) {
            if (U.compareAndSwapInt(this, STATUS, s = status,
                                    (s & ~SMASK) | (tag & SMASK)))
                return (short)s;
        }
    }

    /**
     * Atomically conditionally sets the tag value for this task.
     * Among other applications, tags can be used as visit markers
     * in tasks operating on graphs, as in methods that check: {@code
     * if (task.compareAndSetForkJoinTaskTag((short)0, (short)1))}
     * before processing, otherwise exiting because the node has
     * already been visited.
     *
     * @param e the expected tag value
     * @param tag the new tag value
     * @return {@code true} if successful; i.e., the current value was
     * equal to e and is now tag.
     * @since 1.8
     */
    public final boolean compareAndSetForkJoinTaskTag(short e, short tag) {
        for (int s;;) {
            if ((short)(s = status) != e)
                return false;
            if (U.compareAndSwapInt(this, STATUS, s,
                                    (s & ~SMASK) | (tag & SMASK)))
                return true;
        }
    }

    /**
     * Adaptor for Runnables. This implements RunnableFuture
     * to be compliant with AbstractExecutorService constraints
     * when used in ForkJoinPool.
     */
    static final class AdaptedRunnable<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        final Runnable runnable;
        T result;
        AdaptedRunnable(Runnable runnable, T result) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
            this.result = result; // OK to set this even before completion
        }
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { result = v; }
        public final boolean exec() { runnable.run(); return true; }
        public final void run() { invoke(); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adaptor for Runnables without results
     */
    static final class AdaptedRunnableAction extends ForkJoinTask<Void>
        implements RunnableFuture<Void> {
        final Runnable runnable;
        AdaptedRunnableAction(Runnable runnable) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        public final boolean exec() { runnable.run(); return true; }
        public final void run() { invoke(); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adaptor for Runnables in which failure forces worker exception
     */
    static final class RunnableExecuteAction extends ForkJoinTask<Void> {
        final Runnable runnable;
        RunnableExecuteAction(Runnable runnable) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        public final boolean exec() { runnable.run(); return true; }
        void internalPropagateException(Throwable ex) {
            rethrow(ex); // rethrow outside exec() catches.
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adaptor for Callables
     */
    static final class AdaptedCallable<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        final Callable<? extends T> callable;
        T result;
        AdaptedCallable(Callable<? extends T> callable) {
            if (callable == null) throw new NullPointerException();
            this.callable = callable;
        }
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { result = v; }
        public final boolean exec() {
            try {
                result = callable.call();
                return true;
            } catch (Error err) {
                throw err;
            } catch (RuntimeException rex) {
                throw rex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        public final void run() { invoke(); }
        private static final long serialVersionUID = 2838392045355241008L;
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * a null result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @return the task
     */
    public static ForkJoinTask<?> adapt(Runnable runnable) {
        return new AdaptedRunnableAction(runnable);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * the given result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @param result the result upon completion
     * @param <T> the type of the result
     * @return the task
     */
    public static <T> ForkJoinTask<T> adapt(Runnable runnable, T result) {
        return new AdaptedRunnable<T>(runnable, result);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code call}
     * method of the given {@code Callable} as its action, and returns
     * its result upon {@link #join}, translating any checked exceptions
     * encountered into {@code RuntimeException}.
     *
     * @param callable the callable action
     * @param <T> the type of the callable's result
     * @return the task
     */
    public static <T> ForkJoinTask<T> adapt(Callable<? extends T> callable) {
        return new AdaptedCallable<T>(callable);
    }

    // Serialization support

    private static final long serialVersionUID = -7721805057305804111L;

    /**
     * Saves this task to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData the current run status and the exception thrown
     * during execution, or {@code null} if none
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        s.defaultWriteObject();
        s.writeObject(getException());
    }

    /**
     * Reconstitutes this task from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        Object ex = s.readObject();
        if (ex != null)
            setExceptionalCompletion((Throwable)ex);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long STATUS;
    //初始化在一个静态代码块.
    static {
        exceptionTableLock = new ReentrantLock();
        exceptionTableRefQueue = new ReferenceQueue<Object>();
        exceptionTable = new ExceptionNode[EXCEPTION_MAP_CAPACITY];
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> k = ForkJoinTask.class;
            STATUS = U.objectFieldOffset
                (k.getDeclaredField("status"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
