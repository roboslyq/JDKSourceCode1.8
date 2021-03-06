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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.security.AccessControlContext;
import java.security.ProtectionDomain;
import java.security.Permissions;

/**
 * An {@link ExecutorService} for running {@link ForkJoinTask}s.
 * A {@code ForkJoinPool} provides the entry point for submissions
 * from non-{@code ForkJoinTask} clients, as well as management and
 * monitoring operations.
 *
 * <p>A {@code ForkJoinPool} differs from other kinds of {@link
 * ExecutorService} mainly by virtue of employing
 * <em>work-stealing</em>: all threads in the pool attempt to find and
 * execute tasks submitted to the pool and/or created by other active
 * tasks (eventually blocking waiting for work if none exist). This
 * enables efficient processing when most tasks spawn other subtasks
 * (as do most {@code ForkJoinTask}s), as well as when many small
 * tasks are submitted to the pool from external clients.  Especially
 * when setting <em>asyncMode</em> to true in constructors, {@code
 * ForkJoinPool}s may also be appropriate for use with event-style
 * tasks that are never joined.
 *
 * <p>A static {@link #commonPool()} is available and appropriate for
 * most applications. The common pool is used by any ForkJoinTask that
 * is not explicitly submitted to a specified pool. Using the common
 * pool normally reduces resource usage (its threads are slowly
 * reclaimed during periods of non-use, and reinstated upon subsequent
 * use).
 *
 * <p>For applications that require separate or custom pools, a {@code
 * ForkJoinPool} may be constructed with a given target parallelism
 * level; by default, equal to the number of available processors.
 * The pool attempts to maintain enough active (or available) threads
 * by dynamically adding, suspending, or resuming internal worker
 * threads, even if some tasks are stalled waiting to join others.
 * However, no such adjustments are guaranteed in the face of blocked
 * I/O or other unmanaged synchronization. The nested {@link
 * ManagedBlocker} interface enables extension of the kinds of
 * synchronization accommodated.
 *
 * <p>In addition to execution and lifecycle control methods, this
 * class provides status check methods (for example
 * {@link #getStealCount}) that are intended to aid in developing,
 * tuning, and monitoring fork/join applications. Also, method
 * {@link #toString} returns indications of pool state in a
 * convenient form for informal monitoring.
 *
 * <p>As is the case with other ExecutorServices, there are three
 * main task execution methods summarized in the following table.
 * These are designed to be used primarily by clients not already
 * engaged in fork/join computations in the current pool.  The main
 * forms of these methods accept instances of {@code ForkJoinTask},
 * but overloaded forms also allow mixed execution of plain {@code
 * Runnable}- or {@code Callable}- based activities as well.  However,
 * tasks that are already executing in a pool should normally instead
 * use the within-computation forms listed in the table unless using
 * async event-style tasks that are not usually joined, in which case
 * there is little difference among choice of methods.
 *
 * <table BORDER CELLPADDING=3 CELLSPACING=1>
 * <caption>Summary of task execution methods</caption>
 *  <tr>
 *    <td></td>
 *    <td ALIGN=CENTER> <b>Call from non-fork/join clients</b></td>
 *    <td ALIGN=CENTER> <b>Call from within fork/join computations</b></td>
 *  </tr>
 *  <tr>
 *    <td> <b>Arrange async execution</b></td>
 *    <td> {@link #execute(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork}</td>
 *  </tr>
 *  <tr>
 *    <td> <b>Await and obtain result</b></td>
 *    <td> {@link #invoke(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#invoke}</td>
 *  </tr>
 *  <tr>
 *    <td> <b>Arrange exec and obtain Future</b></td>
 *    <td> {@link #submit(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork} (ForkJoinTasks <em>are</em> Futures)</td>
 *  </tr>
 * </table>
 *
 * <p>The common pool is by default constructed with default
 * parameters, but these may be controlled by setting three
 * {@linkplain System#getProperty system properties}:
 * <ul>
 * <li>{@code java.util.concurrent.ForkJoinPool.common.parallelism}
 * - the parallelism level, a non-negative integer
 * <li>{@code java.util.concurrent.ForkJoinPool.common.threadFactory}
 * - the class name of a {@link ForkJoinWorkerThreadFactory}
 * <li>{@code java.util.concurrent.ForkJoinPool.common.exceptionHandler}
 * - the class name of a {@link UncaughtExceptionHandler}
 * </ul>
 * If a {@link SecurityManager} is present and no factory is
 * specified, then the default pool uses a factory supplying
 * threads that have no {@link Permissions} enabled.
 * The system class loader is used to load these classes.
 * Upon any error in establishing these settings, default parameters
 * are used. It is possible to disable or limit the use of threads in
 * the common pool by setting the parallelism property to zero, and/or
 * using a factory that may return {@code null}. However doing so may
 * cause unjoined tasks to never be executed.
 *
 * <p><b>Implementation notes</b>: This implementation restricts the
 * maximum number of running threads to 32767. Attempts to create
 * pools with greater than the maximum number result in
 * {@code IllegalArgumentException}.
 *
 * <p>This implementation rejects submitted tasks (that is, by throwing
 * {@link RejectedExecutionException}) only when the pool is shut down
 * or internal resources have been exhausted.
 * 一、设计特点：
 *  （1）Fork/Join Pool采用优良的设计、代码实现和硬件原子操作机制等多种思路保证其执行性能：其中包括（但不限于）：
 *          计算资源共享、高性能队列、避免伪共享、工作窃取机制等。
 *  （2）大量使用位运算和位运算技巧
 *  （3）有大量Unsafe原子操作。
 *
 * 二、任务分类：
 * ForkJoinPool 中的任务分为两种：
 *      一种是本地提交的任务（Submission task，如 execute、submit 提交的任务）；
 *      另外一种是 fork 出的子任务（Worker task）。
 * 两种任务都会存放在 WorkQueue 数组中，但是这两种任务并不会混合在同一个队列里，ForkJoinPool 内部使用了一种随机哈希算法
 * （有点类似 ConcurrentHashMap 的桶随机算法）将工作队列与对应的工作线程关联起来，
 * Submission 任务存放在 WorkQueue 数组的偶数索引位置，Worker 任务存放在奇数索引位。实质上，Submission 与 Worker 一样，
 * 只不过他它们被限制只能执行它们提交的本地任务，在后面的源码解析中，我们统一称之为“Worker”。
 *
 * @since 1.7
 * @author Doug Lea
 */
@sun.misc.Contended
public class ForkJoinPool extends AbstractExecutorService {

    /*
     * Implementation Overview
     *
     * This class and its nested classes provide the main
     * functionality and control for a set of worker threads:
     * Submissions from non-FJ threads enter into submission queues.
     * Workers take these tasks and typically split them into subtasks
     * that may be stolen by other workers.  Preference rules give
     * first priority to processing tasks from their own queues (LIFO
     * or FIFO, depending on mode), then to randomized FIFO steals of
     * tasks in other queues.  This framework began as vehicle for
     * supporting tree-structured parallelism using work-stealing.
     * Over time, its scalability advantages led to extensions and
     * changes to better support more diverse usage contexts.  Because
     * most internal methods and nested classes are interrelated,
     * their main rationale and descriptions are presented here;
     * individual methods and nested classes contain only brief
     * comments about details.
     *
     * WorkQueues
     * ==========
     *
     * Most operations occur within work-stealing queues (in nested
     * class WorkQueue).  These are special forms of Deques that
     * support only three of the four possible end-operations -- push,
     * pop, and poll (aka steal), under the further constraints that
     * push and pop are called only from the owning thread (or, as
     * extended here, under a lock), while poll may be called from
     * other threads.  (If you are unfamiliar with them, you probably
     * want to read Herlihy and Shavit's book "The Art of
     * Multiprocessor programming", chapter 16 describing these in
     * more detail before proceeding.)  The main work-stealing queue
     * design is roughly similar to those in the papers "Dynamic
     * Circular Work-Stealing Deque" by Chase and Lev, SPAA 2005
     * (http://research.sun.com/scalable/pubs/index.html) and
     * "Idempotent work stealing" by Michael, Saraswat, and Vechev,
     * PPoPP 2009 (http://portal.acm.org/citation.cfm?id=1504186).
     * The main differences ultimately stem from GC requirements that
     * we null out taken slots as soon as we can, to maintain as small
     * a footprint as possible even in programs generating huge
     * numbers of tasks. To accomplish this, we shift the CAS
     * arbitrating pop vs poll (steal) from being on the indices
     * ("base" and "top") to the slots themselves.
     *
     * Adding tasks then takes the form of a classic array push(task):
     *    q.array[q.top] = task; ++q.top;
     *
     * (The actual code needs to null-check and size-check the array,
     * properly fence the accesses, and possibly signal waiting
     * workers to start scanning -- see below.)  Both a successful pop
     * and poll mainly entail a CAS of a slot from non-null to null.
     *
     * The pop operation (always performed by owner) is:
     *   if ((base != top) and
     *        (the task at top slot is not null) and
     *        (CAS slot to null))
     *           decrement top and return task;
     *
     * And the poll operation (usually by a stealer) is
     *    if ((base != top) and
     *        (the task at base slot is not null) and
     *        (base has not changed) and
     *        (CAS slot to null))
     *           increment base and return task;
     *
     * Because we rely on CASes of references, we do not need tag bits
     * on base or top.  They are simple ints as used in any circular
     * array-based queue (see for example ArrayDeque).  Updates to the
     * indices guarantee that top == base means the queue is empty,
     * but otherwise may err on the side of possibly making the queue
     * appear nonempty when a push, pop, or poll have not fully
     * committed. (Method isEmpty() checks the case of a partially
     * completed removal of the last element.)  Because of this, the
     * poll operation, considered individually, is not wait-free. One
     * thief cannot successfully continue until another in-progress
     * one (or, if previously empty, a push) completes.  However, in
     * the aggregate, we ensure at least probabilistic
     * non-blockingness.  If an attempted steal fails, a thief always
     * chooses a different random victim target to try next. So, in
     * order for one thief to progress, it suffices for any
     * in-progress poll or new push on any empty queue to
     * complete. (This is why we normally use method pollAt and its
     * variants that try once at the apparent base index, else
     * consider alternative actions, rather than method poll, which
     * retries.)
     *
     * This approach also enables support of a user mode in which
     * local task processing is in FIFO, not LIFO order, simply by
     * using poll rather than pop.  This can be useful in
     * message-passing frameworks in which tasks are never joined.
     * However neither mode considers affinities, loads, cache
     * localities, etc, so rarely provide the best possible
     * performance on a given machine, but portably provide good
     * throughput by averaging over these factors.  Further, even if
     * we did try to use such information, we do not usually have a
     * basis for exploiting it.  For example, some sets of tasks
     * profit from cache affinities, but others are harmed by cache
     * pollution effects. Additionally, even though it requires
     * scanning, long-term throughput is often best using random
     * selection rather than directed selection policies, so cheap
     * randomization of sufficient quality is used whenever
     * applicable.  Various Marsaglia XorShifts (some with different
     * shift constants) are inlined at use points.
     *
     * WorkQueues are also used in a similar way for tasks submitted
     * to the pool. We cannot mix these tasks in the same queues used
     * by workers. Instead, we randomly associate submission queues
     * with submitting threads, using a form of hashing.  The
     * ThreadLocalRandom probe value serves as a hash code for
     * choosing existing queues, and may be randomly repositioned upon
     * contention with other submitters.  In essence, submitters act
     * like workers except that they are restricted to executing local
     * tasks that they submitted (or in the case of CountedCompleters,
     * others with the same root task).  Insertion of tasks in shared
     * mode requires a lock (mainly to protect in the case of
     * resizing) but we use only a simple spinlock (using field
     * qlock), because submitters encountering a busy queue move on to
     * try or create other queues -- they block only when creating and
     * registering new queues. Additionally, "qlock" saturates to an
     * unlockable value (-1) at shutdown. Unlocking still can be and
     * is performed by cheaper ordered writes of "qlock" in successful
     * cases, but uses CAS in unsuccessful cases.
     *
     * Management
     * ==========
     *
     * The main throughput advantages of work-stealing stem from
     * decentralized control -- workers mostly take tasks from
     * themselves or each other, at rates that can exceed a billion
     * per second.  The pool itself creates, activates (enables
     * scanning for and running tasks), deactivates, blocks, and
     * terminates threads, all with minimal central information.
     * There are only a few properties that we can globally track or
     * maintain, so we pack them into a small number of variables,
     * often maintaining atomicity without blocking or locking.
     * Nearly all essentially atomic control state is held in two
     * volatile variables that are by far most often read (not
     * written) as status and consistency checks. (Also, field
     * "config" holds unchanging configuration state.)
     *
     * Field "ctl" contains 64 bits holding information needed to
     * atomically decide to add, inactivate, enqueue (on an event
     * queue), dequeue, and/or re-activate workers.  To enable this
     * packing, we restrict maximum parallelism to (1<<15)-1 (which is
     * far in excess of normal operating range) to allow ids, counts,
     * and their negations (used for thresholding) to fit into 16bit
     * subfields.
     *
     * Field "runState" holds lockable state bits (STARTED, STOP, etc)
     * also protecting updates to the workQueues array.  When used as
     * a lock, it is normally held only for a few instructions (the
     * only exceptions are one-time array initialization and uncommon
     * resizing), so is nearly always available after at most a brief
     * spin. But to be extra-cautious, after spinning, method
     * awaitRunStateLock (called only if an initial CAS fails), uses a
     * wait/notify mechanics on a builtin monitor to block when
     * (rarely) needed. This would be a terrible idea for a highly
     * contended lock, but most pools run without the lock ever
     * contending after the spin limit, so this works fine as a more
     * conservative alternative. Because we don't otherwise have an
     * internal Object to use as a monitor, the "stealCounter" (an
     * AtomicLong) is used when available (it too must be lazily
     * initialized; see externalSubmit).
     *
     * Usages of "runState" vs "ctl" interact in only one case:
     * deciding to add a worker thread (see tryAddWorker), in which
     * case the ctl CAS is performed while the lock is held.
     *
     * Recording WorkQueues.  WorkQueues are recorded in the
     * "workQueues" array. The array is created upon first use (see
     * externalSubmit) and expanded if necessary.  Updates to the
     * array while recording new workers and unrecording terminated
     * ones are protected from each other by the runState lock, but
     * the array is otherwise concurrently readable, and accessed
     * directly. We also ensure that reads of the array reference
     * itself never become too stale. To simplify index-based
     * operations, the array size is always a power of two, and all
     * readers must tolerate null slots. Worker queues are at odd
     * indices. Shared (submission) queues are at even indices, up to
     * a maximum of 64 slots, to limit growth even if array needs to
     * expand to add more workers. Grouping them together in this way
     * simplifies and speeds up task scanning.
     *
     * All worker thread creation is on-demand, triggered by task
     * submissions, replacement of terminated workers, and/or
     * compensation for blocked workers. However, all other support
     * code is set up to work with other policies.  To ensure that we
     * do not hold on to worker references that would prevent GC, All
     * accesses to workQueues are via indices into the workQueues
     * array (which is one source of some of the messy code
     * constructions here). In essence, the workQueues array serves as
     * a weak reference mechanism. Thus for example the stack top
     * subfield of ctl stores indices, not references.
     *
     * Queuing Idle Workers. Unlike HPC work-stealing frameworks, we
     * cannot let workers spin indefinitely scanning for tasks when
     * none can be found immediately, and we cannot start/resume
     * workers unless there appear to be tasks available.  On the
     * other hand, we must quickly prod them into action when new
     * tasks are submitted or generated. In many usages, ramp-up time
     * to activate workers is the main limiting factor in overall
     * performance, which is compounded at program start-up by JIT
     * compilation and allocation. So we streamline this as much as
     * possible.
     *
     * The "ctl" field atomically maintains active and total worker
     * counts as well as a queue to place waiting threads so they can
     * be located for signalling. Active counts also play the role of
     * quiescence indicators, so are decremented when workers believe
     * that there are no more tasks to execute. The "queue" is
     * actually a form of Treiber stack.  A stack is ideal for
     * activating threads in most-recently used order. This improves
     * performance and locality, outweighing the disadvantages of
     * being prone to contention and inability to release a worker
     * unless it is topmost on stack.  We park/unpark workers after
     * pushing on the idle worker stack (represented by the lower
     * 32bit subfield of ctl) when they cannot find work.  The top
     * stack state holds the value of the "scanState" field of the
     * worker: its index and status, plus a version counter that, in
     * addition to the count subfields (also serving as version
     * stamps) provide protection against Treiber stack ABA effects.
     *
     * Field scanState is used by both workers and the pool to manage
     * and track whether a worker is INACTIVE (possibly blocked
     * waiting for a signal), or SCANNING for tasks (when neither hold
     * it is busy running tasks).  When a worker is inactivated, its
     * scanState field is set, and is prevented from executing tasks,
     * even though it must scan once for them to avoid queuing
     * races. Note that scanState updates lag queue CAS releases so
     * usage requires care. When queued, the lower 16 bits of
     * scanState must hold its pool index. So we place the index there
     * upon initialization (see registerWorker) and otherwise keep it
     * there or restore it when necessary.
     *
     * Memory ordering.  See "Correct and Efficient Work-Stealing for
     * Weak Memory Models" by Le, Pop, Cohen, and Nardelli, PPoPP 2013
     * (http://www.di.ens.fr/~zappa/readings/ppopp13.pdf) for an
     * analysis of memory ordering requirements in work-stealing
     * algorithms similar to the one used here.  We usually need
     * stronger than minimal ordering because we must sometimes signal
     * workers, requiring Dekker-like full-fences to avoid lost
     * signals.  Arranging for enough ordering without expensive
     * over-fencing requires tradeoffs among the supported means of
     * expressing access constraints. The most central operations,
     * taking from queues and updating ctl state, require full-fence
     * CAS.  Array slots are read using the emulation of volatiles
     * provided by Unsafe.  Access from other threads to WorkQueue
     * base, top, and array requires a volatile load of the first of
     * any of these read.  We use the convention of declaring the
     * "base" index volatile, and always read it before other fields.
     * The owner thread must ensure ordered updates, so writes use
     * ordered intrinsics unless they can piggyback on those for other
     * writes.  Similar conventions and rationales hold for other
     * WorkQueue fields (such as "currentSteal") that are only written
     * by owners but observed by others.
     *
     * Creating workers. To create a worker, we pre-increment total
     * count (serving as a reservation), and attempt to construct a
     * ForkJoinWorkerThread via its factory. Upon construction, the
     * new thread invokes registerWorker, where it constructs a
     * WorkQueue and is assigned an index in the workQueues array
     * (expanding the array if necessary). The thread is then
     * started. Upon any exception across these steps, or null return
     * from factory, deregisterWorker adjusts counts and records
     * accordingly.  If a null return, the pool continues running with
     * fewer than the target number workers. If exceptional, the
     * exception is propagated, generally to some external caller.
     * Worker index assignment avoids the bias in scanning that would
     * occur if entries were sequentially packed starting at the front
     * of the workQueues array. We treat the array as a simple
     * power-of-two hash table, expanding as needed. The seedIndex
     * increment ensures no collisions until a resize is needed or a
     * worker is deregistered and replaced, and thereafter keeps
     * probability of collision low. We cannot use
     * ThreadLocalRandom.getProbe() for similar purposes here because
     * the thread has not started yet, but do so for creating
     * submission queues for existing external threads.
     *
     * Deactivation and waiting. Queuing encounters several intrinsic
     * races; most notably that a task-producing thread can miss
     * seeing (and signalling) another thread that gave up looking for
     * work but has not yet entered the wait queue.  When a worker
     * cannot find a task to steal, it deactivates and enqueues. Very
     * often, the lack of tasks is transient due to GC or OS
     * scheduling. To reduce false-alarm deactivation, scanners
     * compute checksums of queue states during sweeps.  (The
     * stability checks used here and elsewhere are probabilistic
     * variants of snapshot techniques -- see Herlihy & Shavit.)
     * Workers give up and try to deactivate only after the sum is
     * stable across scans. Further, to avoid missed signals, they
     * repeat this scanning process after successful enqueuing until
     * again stable.  In this state, the worker cannot take/run a task
     * it sees until it is released from the queue, so the worker
     * itself eventually tries to release itself or any successor (see
     * tryRelease).  Otherwise, upon an empty scan, a deactivated
     * worker uses an adaptive local spin construction (see awaitWork)
     * before blocking (via park). Note the unusual conventions about
     * Thread.interrupts surrounding parking and other blocking:
     * Because interrupts are used solely to alert threads to check
     * termination, which is checked anyway upon blocking, we clear
     * status (using Thread.interrupted) before any call to park, so
     * that park does not immediately return due to status being set
     * via some other unrelated call to interrupt in user code.
     *
     * Signalling and activation.  Workers are created or activated
     * only when there appears to be at least one task they might be
     * able to find and execute.  Upon push (either by a worker or an
     * external submission) to a previously (possibly) empty queue,
     * workers are signalled if idle, or created if fewer exist than
     * the given parallelism level.  These primary signals are
     * buttressed by others whenever other threads remove a task from
     * a queue and notice that there are other tasks there as well.
     * On most platforms, signalling (unpark) overhead time is
     * noticeably long, and the time between signalling a thread and
     * it actually making progress can be very noticeably long, so it
     * is worth offloading these delays from critical paths as much as
     * possible. Also, because inactive workers are often rescanning
     * or spinning rather than blocking, we set and clear the "parker"
     * field of WorkQueues to reduce unnecessary calls to unpark.
     * (This requires a secondary recheck to avoid missed signals.)
     *
     * Trimming workers. To release resources after periods of lack of
     * use, a worker starting to wait when the pool is quiescent will
     * time out and terminate (see awaitWork) if the pool has remained
     * quiescent for period IDLE_TIMEOUT, increasing the period as the
     * number of threads decreases, eventually removing all workers.
     * Also, when more than two spare threads exist, excess threads
     * are immediately terminated at the next quiescent point.
     * (Padding by two avoids hysteresis.)
     *
     * Shutdown and Termination. A call to shutdownNow invokes
     * tryTerminate to atomically set a runState bit. The calling
     * thread, as well as every other worker thereafter terminating,
     * helps terminate others by setting their (qlock) status,
     * cancelling their unprocessed tasks, and waking them up, doing
     * so repeatedly until stable (but with a loop bounded by the
     * number of workers).  Calls to non-abrupt shutdown() preface
     * this by checking whether termination should commence. This
     * relies primarily on the active count bits of "ctl" maintaining
     * consensus -- tryTerminate is called from awaitWork whenever
     * quiescent. However, external submitters do not take part in
     * this consensus.  So, tryTerminate sweeps through queues (until
     * stable) to ensure lack of in-flight submissions and workers
     * about to process them before triggering the "STOP" phase of
     * termination. (Note: there is an intrinsic conflict if
     * helpQuiescePool is called when shutdown is enabled. Both wait
     * for quiescence, but tryTerminate is biased to not trigger until
     * helpQuiescePool completes.)
     *
     *
     * Joining Tasks
     * =============
     *
     * Any of several actions may be taken when one worker is waiting
     * to join a task stolen (or always held) by another.  Because we
     * are multiplexing many tasks on to a pool of workers, we can't
     * just let them block (as in Thread.join).  We also cannot just
     * reassign the joiner's run-time stack with another and replace
     * it later, which would be a form of "continuation", that even if
     * possible is not necessarily a good idea since we may need both
     * an unblocked task and its continuation to progress.  Instead we
     * combine two tactics:
     *
     *   Helping: Arranging for the joiner to execute some task that it
     *      would be running if the steal had not occurred.
     *
     *   Compensating: Unless there are already enough live threads,
     *      method tryCompensate() may create or re-activate a spare
     *      thread to compensate for blocked joiners until they unblock.
     *
     * A third form (implemented in tryRemoveAndExec) amounts to
     * helping a hypothetical compensator: If we can readily tell that
     * a possible action of a compensator is to steal and execute the
     * task being joined, the joining thread can do so directly,
     * without the need for a compensation thread (although at the
     * expense of larger run-time stacks, but the tradeoff is
     * typically worthwhile).
     *
     * The ManagedBlocker extension API can't use helping so relies
     * only on compensation in method awaitBlocker.
     *
     * The algorithm in helpStealer entails a form of "linear
     * helping".  Each worker records (in field currentSteal) the most
     * recent task it stole from some other worker (or a submission).
     * It also records (in field currentJoin) the task it is currently
     * actively joining. Method helpStealer uses these markers to try
     * to find a worker to help (i.e., steal back a task from and
     * execute it) that could hasten completion of the actively joined
     * task.  Thus, the joiner executes a task that would be on its
     * own local deque had the to-be-joined task not been stolen. This
     * is a conservative variant of the approach described in Wagner &
     * Calder "Leapfrogging: a portable technique for implementing
     * efficient futures" SIGPLAN Notices, 1993
     * (http://portal.acm.org/citation.cfm?id=155354). It differs in
     * that: (1) We only maintain dependency links across workers upon
     * steals, rather than use per-task bookkeeping.  This sometimes
     * requires a linear scan of workQueues array to locate stealers,
     * but often doesn't because stealers leave hints (that may become
     * stale/wrong) of where to locate them.  It is only a hint
     * because a worker might have had multiple steals and the hint
     * records only one of them (usually the most current).  Hinting
     * isolates cost to when it is needed, rather than adding to
     * per-task overhead.  (2) It is "shallow", ignoring nesting and
     * potentially cyclic mutual steals.  (3) It is intentionally
     * racy: field currentJoin is updated only while actively joining,
     * which means that we miss links in the chain during long-lived
     * tasks, GC stalls etc (which is OK since blocking in such cases
     * is usually a good idea).  (4) We bound the number of attempts
     * to find work using checksums and fall back to suspending the
     * worker and if necessary replacing it with another.
     *
     * Helping actions for CountedCompleters do not require tracking
     * currentJoins: Method helpComplete takes and executes any task
     * with the same root as the task being waited on (preferring
     * local pops to non-local polls). However, this still entails
     * some traversal of completer chains, so is less efficient than
     * using CountedCompleters without explicit joins.
     *
     * Compensation does not aim to keep exactly the target
     * parallelism number of unblocked threads running at any given
     * time. Some previous versions of this class employed immediate
     * compensations for any blocked join. However, in practice, the
     * vast majority of blockages are transient byproducts of GC and
     * other JVM or OS activities that are made worse by replacement.
     * Currently, compensation is attempted only after validating that
     * all purportedly active threads are processing tasks by checking
     * field WorkQueue.scanState, which eliminates most false
     * positives.  Also, compensation is bypassed (tolerating fewer
     * threads) in the most common case in which it is rarely
     * beneficial: when a worker with an empty queue (thus no
     * continuation tasks) blocks on a join and there still remain
     * enough threads to ensure liveness.
     *
     * The compensation mechanism may be bounded.  Bounds for the
     * commonPool (see commonMaxSpares) better enable JVMs to cope
     * with programming errors and abuse before running out of
     * resources to do so. In other cases, users may supply factories
     * that limit thread construction. The effects of bounding in this
     * pool (like all others) is imprecise.  Total worker counts are
     * decremented when threads deregister, not when they exit and
     * resources are reclaimed by the JVM and OS. So the number of
     * simultaneously live threads may transiently exceed bounds.
     *
     * Common Pool
     * ===========
     *
     * The static common pool always exists after static
     * initialization.  Since it (or any other created pool) need
     * never be used, we minimize initial construction overhead and
     * footprint to the setup of about a dozen fields, with no nested
     * allocation. Most bootstrapping occurs within method
     * externalSubmit during the first submission to the pool.
     *
     * When external threads submit to the common pool, they can
     * perform subtask processing (see externalHelpComplete and
     * related methods) upon joins.  This caller-helps policy makes it
     * sensible to set common pool parallelism level to one (or more)
     * less than the total number of available cores, or even zero for
     * pure caller-runs.  We do not need to record whether external
     * submissions are to the common pool -- if not, external help
     * methods return quickly. These submitters would otherwise be
     * blocked waiting for completion, so the extra effort (with
     * liberally sprinkled task status checks) in inapplicable cases
     * amounts to an odd form of limited spin-wait before blocking in
     * ForkJoinTask.join.
     *
     * As a more appropriate default in managed environments, unless
     * overridden by system properties, we use workers of subclass
     * InnocuousForkJoinWorkerThread when there is a SecurityManager
     * present. These workers have no permissions set, do not belong
     * to any user-defined ThreadGroup, and erase all ThreadLocals
     * after executing any top-level task (see WorkQueue.runTask).
     * The associated mechanics (mainly in ForkJoinWorkerThread) may
     * be JVM-dependent and must access particular Thread class fields
     * to achieve this effect.
     *
     * Style notes
     * ===========
     *
     * Memory ordering relies mainly on Unsafe intrinsics that carry
     * the further responsibility of explicitly performing null- and
     * bounds- checks otherwise carried out implicitly by JVMs.  This
     * can be awkward and ugly, but also reflects the need to control
     * outcomes across the unusual cases that arise in very racy code
     * with very few invariants. So these explicit checks would exist
     * in some form anyway.  All fields are read into locals before
     * use, and null-checked if they are references.  This is usually
     * done in a "C"-like style of listing declarations at the heads
     * of methods or blocks, and using inline assignments on first
     * encounter.  Array bounds-checks are usually performed by
     * masking with array.length-1, which relies on the invariant that
     * these arrays are created with positive lengths, which is itself
     * paranoically checked. Nearly all explicit checks lead to
     * bypass/return, not exception throws, because they may
     * legitimately arise due to cancellation/revocation during
     * shutdown.
     *
     * There is a lot of representation-level coupling among classes
     * ForkJoinPool, ForkJoinWorkerThread, and ForkJoinTask.  The
     * fields of WorkQueue maintain data structures managed by
     * ForkJoinPool, so are directly accessed.  There is little point
     * trying to reduce this, since any associated future changes in
     * representations will need to be accompanied by algorithmic
     * changes anyway. Several methods intrinsically sprawl because
     * they must accumulate sets of consistent reads of fields held in
     * local variables.  There are also other coding oddities
     * (including several unnecessary-looking hoisted null checks)
     * that help some methods perform reasonably even when interpreted
     * (not compiled).
     *
     * The order of declarations in this file is (with a few exceptions):
     * (1) Static utility functions
     * (2) Nested (static) classes
     * (3) Static fields
     * (4) Fields, along with constants used when unpacking some of them
     * (5) Internal control methods
     * (6) Callbacks and other support for ForkJoinTask methods
     * (7) Exported methods
     * (8) Static block initializing statics in minimally dependent order
     */

    // Static utilities

    /**
     * If there is a security manager, makes sure caller has
     * permission to modify threads.
     * JDK安全检查
     */
    private static void checkPermission() {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkPermission(modifyThreadPermission);
    }

    // Nested classes

    /**
     * Factory for creating new {@link ForkJoinWorkerThread}s.
     * A {@code ForkJoinWorkerThreadFactory} must be defined and used
     * for {@code ForkJoinWorkerThread} subclasses that extend base
     * functionality or initialize threads with different contexts.
     * 内部线程工厂接口，用于创建工作线程ForkJoinWorkerThread
     */
    public static interface ForkJoinWorkerThreadFactory {
        /**
         * Returns a new worker thread operating in the given pool.
         *
         * @param pool the pool this thread works in
         * @return the new worker thread
         * @throws NullPointerException if the pool is null
         */
        public ForkJoinWorkerThread newThread(ForkJoinPool pool);
    }

    /**
     * Default ForkJoinWorkerThreadFactory implementation; creates a
     * new ForkJoinWorkerThread.
     *  内部线程工厂接口默认实现
     */
    static final class DefaultForkJoinWorkerThreadFactory
        implements ForkJoinWorkerThreadFactory {
        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new ForkJoinWorkerThread(pool);
        }
    }

    /**
     * Class for artificial tasks that are used to replace the target
     * of local joins if they are removed from an interior queue slot
     * in WorkQueue.tryRemoveAndExec. We don't need the proxy to
     * actually do anything beyond having a unique identity.
     * 创建一个空Task,内部占位类，用于替换队列中 join 的任务。
     */
    static final class EmptyTask extends ForkJoinTask<Void> {
        private static final long serialVersionUID = -7721805057305804111L;
        EmptyTask() { status = ForkJoinTask.NORMAL; } // force done
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void x) {}
        public final boolean exec() { return true; }
    }

    // Constants shared across ForkJoinPool and WorkQueue
    // ForkJoinPool线程池和WorkQueue工作队列 共享常量参数配置表

    // Bounds：边界值（最大或者最小）
    // SMASK = 0xffff =  0b11111111_11111111 = 2的16次方
    static final int SMASK        = 0xffff;        // short bits == max index //  低位掩码，也是最大索引位
    static final int MAX_CAP      = 0x7fff;        // max #workers - 1  //  工作线程最大容量
    static final int EVENMASK     = 0xfffe;        // even short bits    //  偶数低位掩码
    static final int SQMASK       = 0x007e;        // max 64 (even) slots  //  workQueues 数组最多64个槽位

    // Masks and units for WorkQueue.scanState and ctl sp subfield
    // ctl 子域和 WorkQueue.scanState 的掩码和标志位
    static final int SCANNING     = 1;             // false when running tasks // 标记是否正在运行任务
    static final int INACTIVE     = 1 << 31;       // must be negative  // 失活状态  负数
    static final int SS_SEQ       = 1 << 16;       // version count // 版本戳，防止ABA问题

    // Mode bits for ForkJoinPool.config and WorkQueue.config
    // ForkJoinPool.config 和 WorkQueue.config 的配置信息标记

    static final int MODE_MASK    = 0xffff << 16;  // top half of int  // 模式掩码
    static final int LIFO_QUEUE   = 0;   //LIFO队列
    static final int FIFO_QUEUE   = 1 << 16;//FIFO队列
    static final int SHARED_QUEUE = 1 << 31;       // must be negative  // 共享模式队列，负数

    /**
     * Queues supporting work-stealing as well as external task
     * submission. See above for descriptions and algorithms.
     * Performance on most platforms is very sensitive to placement of
     * instances of both WorkQueues and their arrays -- we absolutely
     * do not want multiple WorkQueue instances or multiple queue
     * arrays sharing cache lines. The @Contended annotation alerts
     * JVMs to try to keep instances apart.
     * ForkJoinPool 的核心数据结构，本质上是work-stealing 模式的双端任务队列，内部存放 ForkJoinTask 对象任务，
     * 它底层是通过数组实现的双端队列，容量为2的幂次，任务队列在首次调用线程池外部方法提交任务之后初始化任务队列，
     * 通过ThreadLocalRandom.probe来计算出任务队列在数组中的索引位置（外部方法调用产生的索引一定是偶数），没有绑定工作线程。
     *
     * 使用 @Contented 注解修饰防止伪共享。
     * 1、工作线程在运行中产生新的任务（通常是因为调用了 fork()）时，此时可以把 WorkQueue 的数据结构视为一个栈，
     *      新的任务会放入栈顶（top 位）；工作线程在处理自己工作队列的任务时，按照 LIFO 的顺序。
     * 2、工作线程在处理自己的工作队列同时，会尝试窃取一个任务（可能是来自于刚刚提交到 pool 的任务，或是来自于其他工作线程的队列任务），
     *      此时可以把 WorkQueue 的数据结构视为一个 FIFO 的队列，窃取的任务位于其他线程的工作队列的队首（base位）。
     * 3、 伪共享状态：缓存系统中是以缓存行（cache line）为单位存储的。缓存行是2的整数幂个连续字节，
     *      一般为32-256个字节。最常见的缓存行大小是64个字节。当多线程修改互相独立的变量时，
     *      如果这些变量共享同一个缓存行，就会无意中影响彼此的性能，这就是伪共享。
     */
    @sun.misc.Contended
    static final class WorkQueue {

        /**
         * Capacity of work-stealing queue array upon initialization.
         * Must be a power of two; at least 4, but should be larger to
         * reduce or eliminate cacheline sharing among queues.
         * Currently, it is much larger, as a partial workaround for
         * the fact that JVMs often place arrays in locations that
         * share GC bookkeeping (especially cardmarks) such that
         * per-write accesses encounter serious memory contention.
         */
        //初始队列容量，2的13幂
        static final int INITIAL_QUEUE_CAPACITY = 1 << 13;

        /**
         * Maximum size for queue arrays. Must be a power of two less
         * than or equal to 1 << (31 - width of array entry) to ensure
         * lack of wraparound of index calculations, but defined to a
         * value a bit less than this to help users trap runaway
         * programs before saturating systems.
         */
        //最大队列容量
        static final int MAXIMUM_QUEUE_CAPACITY = 1 << 26; // 64M

        // Instance fields
        // 实例字段
        /**
         * scanState：
         *      如果WorkQueue没有属于自己的owner(下标为偶数的都没有),该值为 inactive 也就是一个负数。
         *      如果有自己的owner，该值的初始值为其在WorkQueue[]数组中的下标，也肯定是个奇数。
         *      如果这个值，变成了偶数，说明该队列所属的Thread正在执行Task
         *  static final int SCANNING = 1; // false when running tasks
         *  static final int INACTIVE = 1 << 31; // must be negative
         */
        volatile int scanState;    // versioned, <0: inactive; odd:scanning // Woker状态： 偶数表示RUNNING 奇数表示SCANNING 负数表示inactive
        // 记录前任的 idle worker
        int stackPred;             // pool stack (ctl) predecessor// 记录前一个栈顶的idle worker
        int nsteals;               // number of steals // 偷取任务数
        int hint;                  // randomization and stealer index hint // 记录偷取者索引，初始为随机索引
        /**
         * 为偶数的WorkQueue,则其mode是共享类型。如果有自己的owner 默认是 LIFO
         */
        int config;                // pool index and mode  // 池索引和模式(index | mode)
        /**
         * 锁标识,在多线程往队列中添加数据，会有竞争，使用此标识抢占锁。
         */
        volatile int qlock;        // 1: locked, < 0: terminate; else 0
        /**
         * worker steal的偏移量,因为其他的线程都可以偷该队列的任务,所有base使用volatile标识。(FIFO)
         * 简单来说，就是steal work的下一个出队元素的索引位（主要是为线程窃取准备的索引位置）
         */
        volatile int base;         // index of next slot for poll //下一个poll操作的索引（栈底/队列头）
        /**
         * owner执行任务的偏移量。(LIFO)
         * 即owner线程的工作任务获取索引位置
         */
        int top;                   // index of next slot for push //  下一个push操作的索引（栈顶/队列尾）
        ForkJoinTask<?>[] array;   // the elements (initially unallocated) // 任务数组
        // 队列所属的ForkJoinPool（可能为空）
        // 注意，一个ForkJoinPool中会有多个执行线程，还会有比执行线程更多的（或一样多的）队列
        final ForkJoinPool pool;   // the containing pool (may be null)     当前工作队列对应的pool

        // 这个队列所属的归并计算工作线程。注意，工作队列也可能不属于任何工作线程
        final ForkJoinWorkerThread owner; // owning thread or null if shared  // 当前工作队列的工作线程，共享模式下为null
        /**
         * 如果 owner 挂起，则使用该变量做记录。
         */
        volatile Thread parker;    // == owner during call to park; else null // 调用park阻塞期间为owner，其他情况为null
        /**
         * 当前正在join等待结果的任务。
         */
        volatile ForkJoinTask<?> currentJoin;  // task being joined in awaitJoin // 记录被join过来的任务
        /**
         * 当前执行的任务是steal过来的任务，该变量做记录
         */
        volatile ForkJoinTask<?> currentSteal; // mainly used by helpStealer // 记录从其他工作队列偷取过来的任务

        WorkQueue(ForkJoinPool pool, ForkJoinWorkerThread owner) {
            this.pool = pool;
            this.owner = owner;
            // Place indices in the center of array (that is not yet allocated)
            //向右移一位，相当于除以2，即初始容量一半
            base = top = INITIAL_QUEUE_CAPACITY >>> 1;
        }

        /**
         * Returns an exportable index (used by ForkJoinWorkerThread).
         */
        final int getPoolIndex() {
            return (config & 0xffff) >>> 1; // ignore odd/even tag bit
        }

        /**
         * Returns the approximate number of tasks in the queue.
         * 返回队列中大概的任务数量 (栈底-栈顶)（因为任务在不断的变化，所以不是很准确）
         */
        final int queueSize() {
            int n = base - top;       // non-owner callers must read base first
            return (n >= 0) ? 0 : -n; // ignore transient negative
        }

        /**
         * Provides a more accurate estimate of whether this queue has
         * any tasks than does queueSize, by checking whether a
         * near-empty（几乎为空） queue has at least one unclaimed(无人认领的) task.
         */
        final boolean isEmpty() {
            ForkJoinTask<?>[] a;
            int n, // base - top
                m, //  a.length - 1
                s; //top
            return ((n = base - (s = top)) >= 0 || // 有任务时,base - top >=0 ，即n>=0
                    (n == -1 &&           // possibly one task
                         ((a = array) == null || (m = a.length - 1) < 0 ||
                         U.getObject
                         (a, (long)((m & (s - 1)) << ASHIFT) + ABASE) == null)
                    )
                    );
        }

        /**
         * Pushes a task. Call only by owner in unshared queues.  (The
         * shared-queue version is embedded in method externalPush.)
         *
         * @param task the task. Caller must ensure non-null.
         * @throws RejectedExecutionException if array cannot be resized
         * 当ForkJoinWorkerThread需要向双端队列中放入一个新的待执行子任务时，会调用WorkQueue中的push方法.
         * 将任务存入自身队列的栈顶（top）。常见的操作是ForkJionTask.fork()方法被调用。
         */
        final void push(ForkJoinTask<?> task) {
            ForkJoinTask<?>[] a; //现有任务数组：array
            ForkJoinPool p;
            int b = base,
                s = top,
                 n;// s - b，当前任务队列中还有多少个任务
            // 请注意，在执行task.fork时，触发push情况下，array不会为null
            // 因为在这之前workqueue中的array已经完成了初始化（在工作线程初始化时就完成了）
            if ((a = array) != null) {    // ignore if queue removed ，如果现在任务数据array为空，则忽略
                int m = a.length - 1;     // fenced write for task visibility 不为空，则取m = a.length - 1,即数组下标最大值
                /**
                 * 将task放入workQueue
                 * U常量是java底层的sun.misc.Unsafe操作类，这个类提供硬件级别的原子操作
                 * putOrderedObject方法在指定的对象a中，指定的内存偏移量的位置，赋予一个新的元素
                 * 具体的偏移量为：((m & s) << ASHIFT) + ABASE
                 */
                U.putOrderedObject(a, ((m & s) << ASHIFT) + ABASE, task);

                // putOrderedInt方法对当前指定的对象中的指定字段，进行赋值操作
                // 这里的代码意义是将workQueue对象本身中的top标示的位置 + 1，
                U.putOrderedInt(this, QTOP, s + 1);
                if ((n = s - b) <= 1) {//表示当前任务队列中只有一个任务或者0个任务
                    if ((p = pool) != null)
                        //新建或唤醒一个工作线程
                        //signalWork方法的意义在于，在当前活动的工作线程过少的情况下，创建新的工作线程
                        p.signalWork(p.workQueues, this);
                }
                // 如果array的剩余空间不够了，则进行增加
                else if (n >= m)//当任务队列中的任务n大于或者等于数组的大小m,表示数组已经不够，将放不下任务了，需要扩容。
                    growArray();
            }
        }

        /**
         * Initializes or doubles the capacity of array. Call either
         * by owner or with lock held -- it is OK for base, but not
         * top, to move while resizings are in progress.
         * 初始化或者双倍扩容任务数组
         */
        final ForkJoinTask<?>[] growArray() {
            ForkJoinTask<?>[] oldA = array;
            //不为空时，左移1位(扩容1倍)，否则大小为INITIAL_QUEUE_CAPACITY
            int size = oldA != null ? oldA.length << 1 : INITIAL_QUEUE_CAPACITY;
            if (size > MAXIMUM_QUEUE_CAPACITY)
                throw new RejectedExecutionException("Queue capacity exceeded");
            int oldMask,
                     t,
                     b;
            ForkJoinTask<?>[] a = array = new ForkJoinTask<?>[size]; //将array指向新数组
            if (oldA != null && (oldMask = oldA.length - 1) >= 0 &&
                (t = top) - (b = base) > 0) {
                int mask = size - 1;
                do { // emulate poll from old array, push to new array
                    //模拟从旧数组轮询，推到新数组
                    ForkJoinTask<?> x;
                    int oldj = ((b & oldMask) << ASHIFT) + ABASE;
                    int j    = ((b &    mask) << ASHIFT) + ABASE;
                    x = (ForkJoinTask<?>)U.getObjectVolatile(oldA, oldj);
                    if (x != null &&
                        U.compareAndSwapObject(oldA, oldj, x, null))
                        U.putObjectVolatile(a, j, x);
                } while (++b != t);
            }
            return a;
        }

        /**
         * Takes next task, if one exists, in LIFO order.  Call only
         * by owner in unshared queues.
         * 从栈顶弹出任务（只有owner这个从栈顶获取LIFO，stealer从栈底获取FIFO）
         */
        final ForkJoinTask<?> pop() {
            ForkJoinTask<?>[] a; ForkJoinTask<?> t; int m;
            if ((a = array) != null && (m = a.length - 1) >= 0) {
                //循环获取任务（因为当前任务可能被其实线程先取走了，所以需要循环）
                for (int s; (s = top - 1) - base >= 0;) {
                    long j = ((m & s) << ASHIFT) + ABASE;
                    //任务为空，结束，返回null
                    if ((t = (ForkJoinTask<?>)U.getObject(a, j)) == null)
                        break;
                    //CAS 取任务
                    if (U.compareAndSwapObject(a, j, t, null)) {
                        U.putOrderedInt(this, QTOP, s);
                        return t;
                    }
                }
            }
            return null;
        }

        /**
         * Takes a task in FIFO order if b is base of queue and a task
         * can be claimed without contention. Specialized versions
         * appear in ForkJoinPool methods scan and helpStealer.
         * 窃取任务使用(FIFO),专门的版本出现在ForkJoinPool方法scan和helpStealer中
         */
        final ForkJoinTask<?> pollAt(int b) {
            ForkJoinTask<?> t; ForkJoinTask<?>[] a;
            if ((a = array) != null) {
                int j = (((a.length - 1) & b) << ASHIFT) + ABASE;
                if ((t = (ForkJoinTask<?>)U.getObjectVolatile(a, j)) != null &&
                    base == b && U.compareAndSwapObject(a, j, t, null)) {
                    base = b + 1;
                    return t;
                }
            }
            return null;
        }

        /**
         * Takes next task, if one exists, in FIFO order.
         * 从栈底(队尾)窃取任务
         */
        final ForkJoinTask<?> poll() {
            ForkJoinTask<?>[] a; int b; ForkJoinTask<?> t;
            while ((b = base) - top < 0 && (a = array) != null) {
                int j = (((a.length - 1) & b) << ASHIFT) + ABASE;
                t = (ForkJoinTask<?>)U.getObjectVolatile(a, j);
                if (base == b) {
                    if (t != null) {
                        if (U.compareAndSwapObject(a, j, t, null)) {
                            base = b + 1;
                            return t;
                        }
                    }
                    else if (b + 1 == top) // now empty
                        break;
                }
            }
            return null;
        }

        /**
         * Takes next task, if one exists, in order specified by mode.
         * 如果asyncMode设定为后进先出（LIFO）
         * 则使用pop()从双端队列的前端取出任务
         * 否则就是先进先出模式（FIFO），使用poll()从双端队列的后端取出任务
         */
        final ForkJoinTask<?> nextLocalTask() {
            return (config & FIFO_QUEUE) == 0 ? pop() : poll();
        }

        /**
         * Returns next task, if one exists, in order specified by mode.
         * 返回任务，但不改变top的base值
         */
        final ForkJoinTask<?> peek() {
            ForkJoinTask<?>[] a = array; int m;
            if (a == null || (m = a.length - 1) < 0)
                return null;
            int i = (config & FIFO_QUEUE) == 0 ? top - 1 : base;
            int j = ((i & m) << ASHIFT) + ABASE;
            return (ForkJoinTask<?>)U.getObjectVolatile(a, j);
        }

        /**
         * Pops the given task only if it is at the current top.
         * (A shared version is available only via FJP.tryExternalUnpush)
         * 从workqueue获取指定的任务t,如果任务t刚好处理当前栈的栈顶则返回true,否则返回false。
        */
        final boolean tryUnpush(ForkJoinTask<?> t) {
            ForkJoinTask<?>[] a; int s;
            if ((a = array) != null && (s = top) != base &&
                //将栈顶元素置空，如果成功则说明栈顶元素和t相等
                U.compareAndSwapObject
                (a, (((a.length - 1) & --s) << ASHIFT) + ABASE, t, null)) {
                //元素-1
                U.putOrderedInt(this, QTOP, s);
                return true;
            }
            return false;
        }

        /**
         * Removes and cancels all known tasks, ignoring any exceptions.
         * 移除并取消所有已经添加的任务，此操作会忽略任何异常。
         */
        final void cancelAll() {
            ForkJoinTask<?> t;
            //清除当前join任务
            if ((t = currentJoin) != null) {
                currentJoin = null;
                ForkJoinTask.cancelIgnoringExceptions(t);
            }
            //清除当前steal的任务
            if ((t = currentSteal) != null) {
                currentSteal = null;
                ForkJoinTask.cancelIgnoringExceptions(t);
            }
            //清除当前队列任务，一直使用弹出
            while ((t = poll()) != null)
                ForkJoinTask.cancelIgnoringExceptions(t);
        }

        // Specialized execution methods

        /**
         * Polls and runs tasks until empty.
         * 执行所有的任务，循环任务列表
         */
        final void pollAndExecAll() {
            for (ForkJoinTask<?> t; (t = poll()) != null;)
                t.doExec();
        }

        /**
         * Removes and executes all local tasks. If LIFO, invokes
         * pollAndExecAll. Otherwise implements a specialized pop loop
         * to exec until empty.
         * 删除并执行所有本地任务。如果是LIFO,调用pollAndExecAll(),否则需要实现一个专门的pop循环执行任务直到清空。
         *
         */
        final void execLocalTasks() {
            int b = base, m, s;
            ForkJoinTask<?>[] a = array;
            if (b - (s = top - 1) <= 0 && a != null &&
                (m = a.length - 1) >= 0) {
                //FIFO队列
                if ((config & FIFO_QUEUE) == 0) {
                    for (ForkJoinTask<?> t;;) {
                        if ((t = (ForkJoinTask<?>)U.getAndSetObject
                             (a, ((m & s) << ASHIFT) + ABASE, null)) == null)
                            break;
                        U.putOrderedInt(this, QTOP, s);
                        t.doExec();
                        if (base - (s = top - 1) > 0)
                            break;
                    }
                }
                //LIFO队列
                else
                    pollAndExecAll();
            }
        }

        /**
         * Executes the given task and any remaining local tasks.
         * 执行给的任务task,并且同时执行本地所有的任务。
         */
        final void runTask(ForkJoinTask<?> task) {
            if (task != null) {
                /**
                 * 首先scanState &= ~SCANNING;标识该线程处于繁忙状态。
                 *      1、执行偷取的Task。
                 *      2、调用execLocalTasks对线程所属的WorkQueue内的任务进行LIFO执行。
                 */
                scanState &= ~SCANNING; // mark as busy
                (currentSteal = task).doExec();
                U.putOrderedObject(this, QCURRENTSTEAL, null); // release for GC
                execLocalTasks();
                ForkJoinWorkerThread thread = owner;
                if (++nsteals < 0)      // collect on overflow
                    transferStealCount(pool);
                scanState |= SCANNING;
                if (thread != null)
                    thread.afterTopLevelExec();
            }
        }

        /**
         * Adds steal count to pool stealCounter if it exists, and resets.
         * 添加steal的笔数到steal计数器
         */
        final void transferStealCount(ForkJoinPool p) {
            AtomicLong sc;
            if (p != null && (sc = p.stealCounter) != null) {
                int s = nsteals;
                nsteals = 0;            // if negative, correct for overflow
                sc.getAndAdd((long)(s < 0 ? Integer.MAX_VALUE : s));
            }
        }

        /**
         * If present, removes from queue and executes the given task,
         * or any other cancelled task. Used only by awaitJoin.
         *
         * @return true if queue empty and task not known to be done
         */
        final boolean tryRemoveAndExec(ForkJoinTask<?> task) {
            ForkJoinTask<?>[] a; int m, s, b, n;
            if ((a = array) != null && (m = a.length - 1) >= 0 &&
                task != null) {
                while ((n = (s = top) - (b = base)) > 0) {
                    for (ForkJoinTask<?> t;;) {      // traverse from s to b
                        long j = ((--s & m) << ASHIFT) + ABASE;
                        if ((t = (ForkJoinTask<?>)U.getObject(a, j)) == null)
                            return s + 1 == top;     // shorter than expected
                        else if (t == task) {
                            boolean removed = false;
                            if (s + 1 == top) {      // pop
                                if (U.compareAndSwapObject(a, j, task, null)) {
                                    U.putOrderedInt(this, QTOP, s);
                                    removed = true;
                                }
                            }
                            else if (base == b)      // replace with proxy
                                removed = U.compareAndSwapObject(
                                    a, j, task, new EmptyTask());
                            if (removed)
                                task.doExec();
                            break;
                        }
                        else if (t.status < 0 && s + 1 == top) {
                            if (U.compareAndSwapObject(a, j, t, null))
                                U.putOrderedInt(this, QTOP, s);
                            break;                  // was cancelled
                        }
                        if (--n == 0)
                            return false;
                    }
                    if (task.status < 0)
                        return false;
                }
            }
            return true;
        }

        /**
         * Pops task if in the same CC computation as the given task,
         * in either shared or owned mode. Used only by helpComplete.
         */
        final CountedCompleter<?> popCC(CountedCompleter<?> task, int mode) {
            int s; ForkJoinTask<?>[] a; Object o;
            if (base - (s = top) < 0 && (a = array) != null) {
                long j = (((a.length - 1) & (s - 1)) << ASHIFT) + ABASE;
                if ((o = U.getObjectVolatile(a, j)) != null &&
                    (o instanceof CountedCompleter)) {
                    CountedCompleter<?> t = (CountedCompleter<?>)o;
                    for (CountedCompleter<?> r = t;;) {
                        if (r == task) {
                            if (mode < 0) { // must lock
                                if (U.compareAndSwapInt(this, QLOCK, 0, 1)) {
                                    if (top == s && array == a &&
                                        U.compareAndSwapObject(a, j, t, null)) {
                                        U.putOrderedInt(this, QTOP, s - 1);
                                        U.putOrderedInt(this, QLOCK, 0);
                                        return t;
                                    }
                                    U.compareAndSwapInt(this, QLOCK, 1, 0);
                                }
                            }
                            else if (U.compareAndSwapObject(a, j, t, null)) {
                                U.putOrderedInt(this, QTOP, s - 1);
                                return t;
                            }
                            break;
                        }
                        else if ((r = r.completer) == null) // try parent
                            break;
                    }
                }
            }
            return null;
        }

        /**
         * Steals and runs a task in the same CC computation as the
         * given task if one exists and can be taken without
         * contention. Otherwise returns a checksum/control value for
         * use by method helpComplete.
         *
         * @return 1 if successful, 2 if retryable (lost to another
         * stealer), -1 if non-empty but no matching task found, else
         * the base index, forced negative.
         */
        final int pollAndExecCC(CountedCompleter<?> task) {
            int b, h; ForkJoinTask<?>[] a; Object o;
            if ((b = base) - top >= 0 || (a = array) == null)
                h = b | Integer.MIN_VALUE;  // to sense movement on re-poll
            else {
                long j = (((a.length - 1) & b) << ASHIFT) + ABASE;
                if ((o = U.getObjectVolatile(a, j)) == null)
                    h = 2;                  // retryable
                else if (!(o instanceof CountedCompleter))
                    h = -1;                 // unmatchable
                else {
                    CountedCompleter<?> t = (CountedCompleter<?>)o;
                    for (CountedCompleter<?> r = t;;) {
                        if (r == task) {
                            if (base == b &&
                                U.compareAndSwapObject(a, j, t, null)) {
                                base = b + 1;
                                t.doExec();
                                h = 1;      // success
                            }
                            else
                                h = 2;      // lost CAS
                            break;
                        }
                        else if ((r = r.completer) == null) {
                            h = -1;         // unmatched
                            break;
                        }
                    }
                }
            }
            return h;
        }

        /**
         * Returns true if owned and not known to be blocked.
         */
        final boolean isApparentlyUnblocked() {
            Thread wt; Thread.State s;
            return (scanState >= 0 &&
                    (wt = owner) != null &&
                    (s = wt.getState()) != Thread.State.BLOCKED &&
                    s != Thread.State.WAITING &&
                    s != Thread.State.TIMED_WAITING);
        }

        // Unsafe mechanics. Note that some are (and must be) the same as in FJP
        private static final sun.misc.Unsafe U;
        private static final int  ABASE;
        private static final int  ASHIFT;
        private static final long QTOP;
        private static final long QLOCK;
        private static final long QCURRENTSTEAL;
        static {
            try {
                U = sun.misc.Unsafe.getUnsafe();
                Class<?> wk = WorkQueue.class;
                Class<?> ak = ForkJoinTask[].class;
                QTOP = U.objectFieldOffset
                    (wk.getDeclaredField("top"));
                QLOCK = U.objectFieldOffset
                    (wk.getDeclaredField("qlock"));
                QCURRENTSTEAL = U.objectFieldOffset
                    (wk.getDeclaredField("currentSteal"));
                ABASE = U.arrayBaseOffset(ak);
                int scale = U.arrayIndexScale(ak);
                if ((scale & (scale - 1)) != 0)
                    throw new Error("data type scale not a power of two");
                ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    // static fields (initialized in static initializer below)

    /**
     * Creates a new ForkJoinWorkerThread. This factory is used unless
     * overridden in ForkJoinPool constructors.
     */
    /** 静态初始化字段 */
    //线程工厂
    public static final ForkJoinWorkerThreadFactory
        defaultForkJoinWorkerThreadFactory;

    /**
     * Permission required for callers of methods that may start or
     * kill threads.
     */
    //启动或杀死线程的方法调用者的权限
    private static final RuntimePermission modifyThreadPermission;

    /**
     * Common (static) pool. Non-null for public use unless a static
     * construction exception, but internal usages null-check on use
     * to paranoically avoid potential initialization circularities
     * as well as to simplify generated code.
     */
    // 公共静态pool，默认的线程池
    static final ForkJoinPool common;

    /**
     * Common pool parallelism. To allow simpler use and management
     * when common pool threads are disabled, we allow the underlying
     * common.parallelism field to be zero, but in that case still report
     * parallelism as 1 to reflect resulting caller-runs mechanics.
     */
    //并行度，对应内部common池
    static final int commonParallelism;

    /**
     * Limit on spare thread construction in tryCompensate.
     */
    //备用线程数，在tryCompensate中使用
    private static int commonMaxSpares;

    /**
     * Sequence number for creating workerNamePrefix.
     */
    //创建workerNamePrefix(工作线程名称前缀)时的序号
    private static int poolNumberSequence;

    /**
     * Returns the next sequence number. We don't expect this to
     * ever contend, so use simple builtin sync.
     */
    private static final synchronized int nextPoolId() {
        return ++poolNumberSequence;
    }

    // static configuration constants

    /**
     * Initial timeout value (in nanoseconds) for the thread
     * triggering quiescence to park waiting for new work. On timeout,
     * the thread will instead try to shrink the number of
     * workers. The value should be large enough to avoid overly
     * aggressive shrinkage during most transient stalls (long GCs
     * etc).
     */
    //线程阻塞等待新的任务的超时值(以纳秒为单位)，默认2秒
    private static final long IDLE_TIMEOUT = 2000L * 1000L * 1000L; // 2sec

    /**
     * Tolerance for idle timeouts, to cope with timer undershoots
     */
    //空闲超时时间，防止timer未命中
     private static final long TIMEOUT_SLOP = 20L * 1000L * 1000L;  // 20ms

    /**
     * The initial value for commonMaxSpares during static
     * initialization. The value is far in excess of normal
     * requirements, but also far short of MAX_CAP and typical
     * OS thread limits, so allows JVMs to catch misuse/abuse
     * before running out of resources needed to do so.
     */
    //默认备用线程数
    private static final int DEFAULT_COMMON_MAX_SPARES = 256;

    /**
     * Number of times to spin-wait before blocking. The spins (in
     * awaitRunStateLock and awaitWork) currently use randomized
     * spins. Currently set to zero to reduce CPU usage.
     *
     * If greater than zero the value of SPINS must be a power
     * of two, at least 4.  A value of 2048 causes spinning for a
     * small fraction of typical context-switch times.
     *
     * If/when MWAIT-like intrinsics becomes available, they
     * may allow quieter spinning.
     */
    //阻塞前自旋的次数，用在在awaitRunStateLock和awaitWork中
    private static final int SPINS  = 0;

    /**
     * Increment for seed generators. See class ThreadLocal for
     * explanation.
     */
    //indexSeed的增量
    private static final int SEED_INCREMENT = 0x9e3779b9;

    /*
     * Bits and masks for field ctl, packed with 4 16 bit subfields:
     * AC: Number of active running workers minus target parallelism
     * TC: Number of total workers minus target parallelism
     * SS: version count and status of top waiting thread
     * ID: poolIndex of top of Treiber stack of waiters
     *
     * When convenient, we can extract the lower 32 stack top bits
     * (including version bits) as sp=(int)ctl.  The offsets of counts
     * by the target parallelism and the positionings of fields makes
     * it possible to perform the most common checks via sign tests of
     * fields: When ac is negative, there are not enough active
     * workers, when tc is negative, there are not enough total
     * workers.  When sp is non-zero, there are waiting workers.  To
     * deal with possibly negative fields, we use casts in and out of
     * "short" and/or signed shifts to maintain signedness.
     *
     * Because it occupies uppermost bits, we can add one active count
     * using getAndAddLong of AC_UNIT, rather than CAS, when returning
     * from a blocked join.  Other updates entail multiple subfields
     * and masking, requiring CAS.
     *
     */
    //================ForkJoinPool 中的相关常量和实例字段

    // Lower and upper word masks
    //  低位和高位掩码(掩码是一串二进制代码对目标字段进行位与运算，屏蔽当前的输入位)
    private static final long SP_MASK    = 0xffffffffL;
    private static final long UC_MASK    = ~SP_MASK;

    // Active counts
    // 活跃线程数

    private static final int  AC_SHIFT   = 48;
    //2的48次方，0b00000001_00000000_00000000_00000000_00000000_00000000_00000000
    private static final long AC_UNIT    = 0x0001L << AC_SHIFT;  //活跃线程数增量（每次加1）
    //0b11111111_11111111_00000000_00000000_00000000_00000000_00000000_00000000
    private static final long AC_MASK    = 0xffffL << AC_SHIFT;//活跃线程数掩码,即高16位为1，其它48位为0

    // Total counts
    // 工作线程数

    private static final int  TC_SHIFT   = 32;
    private static final long TC_UNIT    = 0x0001L << TC_SHIFT; //工作线程数增量（每次加1）
    private static final long TC_MASK    = 0xffffL << TC_SHIFT;  //掩码
    private static final long ADD_WORKER = 0x0001L << (TC_SHIFT + 15); // sign // 创建工作线程标志


    // runState bits: SHUTDOWN must be negative, others arbitrary powers of two

    /**
     *  池状态(ForkJoinPool的状态)
     *  1、池状态设计很巧妙，通过1位移运算得到的结果，只有特定的一位不为0，其它位均为0.这样就可以通过 & 运算，判断是否处于当前状态。
     *  或者通过 | 运算设置为当前状态而不改变其它位的值。
     *  速度快，性能好
      */


    private static final int  RSLOCK     = 1; // 可以通过runState & RSLOCK 来判断是否处于锁状态。如果为0，表示无锁，如果为1，表示有锁。
    private static final int  RSIGNAL    = 1 << 1;
    private static final int  STARTED    = 1 << 2;
    private static final int  STOP       = 1 << 29;
    //0b01000000_00000000_00000000_00000000
    private static final int  TERMINATED = 1 << 30;
    //0b10000000_00000000_00000000_00000000 = -2147483648
    private static final int  SHUTDOWN   = 1 << 31; //负数 ，其他都是正数

    // Instance fields
    // 实例字段
    /**
     *  一、ctl是ForkJoinPool中最重要的，也是设计最精密的域，它是整个ForkJoinPool的总控信息。所有信息包含在一个long(64bit)中，这些信息包括：
     *      当前活动的工作线程数量
     *      当前总的工作线程数量
     *      ForkJoinPool的关闭标志
     *      在Treiber stack(由全部等待工作线程组成的一个链)顶端等待的工作线程的等待次数
     *      Treiber stack(由全部等待工作线程组成的一个链)顶端等待的工作线程的ID信息(工作线程的下标取反)。
     *  ctl还有一个相对不重要的作用就是，某些非volatile域会依赖ctl来保证可见性。
     *
     * 二、ForkJoinPool的总控制信息，包含在一个long里面：
     *      AC: 表示当前活动的工作线程的数量减去并行度得到的数值。(16 bits)
     *      TC: 表示全部工作线程的数量减去并行度得到的数值。(16bits)
     *      ST: 表示当前ForkJoinPool是否正在关闭。(1 bit)
     *      EC: 表示Treiber stack顶端的等待工作线程的等待次数。(15 bits)
     *      ID: Treiber stack顶端的等待工作线程的下标取反(16 bits)
     *
     * 1111111111111111 1111111111111111  1  111111111111111_11111111_11111111
     * AC               TC                ST EC              ID
     *
     *      如果AC为负数，说明没有足够的活动工作线程。
     *      如果TC为负数，说明工作线程数量没达到最大工作线程数量。
     *      如果ID为负数，说明至少有一个等待的工作线程。
     *      如果(int)ctl为负数，说明ForkJoinPool正在关闭。
     */
    volatile long ctl;                   // main pool control // 主控制参数
    volatile int runState;               // lockable status // 记录了ForkJoinPool的运行状态
    final int config;                    // parallelism, mode // 并行度|模式
    int indexSeed;                       // to generate worker index  // 用于生成工作线程索引
    volatile WorkQueue[] workQueues;     // main registry // 主对象注册信息，workQueue
    final ForkJoinWorkerThreadFactory factory; // 线程工厂
    final UncaughtExceptionHandler ueh;  // per-worker UEH // 每个工作线程的异常信息
    final String workerNamePrefix;       // to create worker name string // 用于创建工作线程的名称
    volatile AtomicLong stealCounter;    // also used as sync monitor  // 偷取任务总数，也可作为同步监视器

    /**
     * Acquires the runState lock; returns current (locked) runState.
     *    在多线程环境修改runState,需要先获取锁，RSLOCK和RSIGNAL就用在这里
     *    用lockRunState锁定，检查当前状态，尝试一次使用CAS修改runState为RSLOCK。
     *    需要状态变化的机会很少，大多数时间一次就能成功，但不能排除少几率的竞争，这时候进入awaitRunStateLock
     */
    private int lockRunState() {
        int rs;
        return ((((rs = runState) & RSLOCK) != 0 ||
                 !U.compareAndSwapInt(this, RUNSTATE, rs, rs |= RSLOCK)) ?
                awaitRunStateLock() : rs);
    }

    /**
     * Spins and/or blocks until runstate lock is available.  See
     * above for explanation.
     */
    private int awaitRunStateLock() {
        Object lock;
        boolean wasInterrupted = false;
        //自旋开始
        for (int spins = SPINS, r = 0, rs, ns;;) {
            //第一步，mark1再次尝试修改runState为RSLOCK，成功直接返回。
            if (((rs = runState) & RSLOCK) == 0) {
                // rs | RSLOCK表示加锁，因为最低位为1，或运算之后一定会为1，并且不会改变其它位
                if (U.compareAndSwapInt(this, RUNSTATE, rs, ns = rs | RSLOCK)) {
                    if (wasInterrupted) {
                        try {
                            Thread.currentThread().interrupt();
                        } catch (SecurityException ignore) {
                        }
                    }
                    //加锁成功
                    return ns;
                }
            }
            else if (r == 0)
                r = ThreadLocalRandom.nextSecondarySeed();
            else if (spins > 0) {
                r ^= r << 6; r ^= r >>> 21; r ^= r << 7; // xorshift
                if (r >= 0)
                    --spins;
            }
            //ForkJoinPool初始化情况，这里没有额外多写个变量做锁，直接利用了stealCounter这个原子变量。
            // 因为初始化时（externalSubmit()），才会对stealCounter赋值,所以当状态不是STARTED或者stealCounter为空时，让出线程等待。
            else if ((rs & STARTED) == 0 || (lock = stealCounter) == null)
                Thread.yield();   // initialization race
            //线程不会无限制自旋尝试，会利用wait/notify进入阻塞等待。RSIGNAL代替原状态，表示有线程进入了等待，解锁时要处理。
            // 在高并发下，这不是一个好的设计，但进入这里的几率很低，作为兜底还是可以的
            else if (U.compareAndSwapInt(this, RUNSTATE, rs, rs | RSIGNAL)) {
                synchronized (lock) {
                    if ((runState & RSIGNAL) != 0) { //如果为0，表示是持有者进来了，直接发起notifyAll()操作。
                        try {
                            lock.wait();
                        } catch (InterruptedException ie) {
                            if (!(Thread.currentThread() instanceof
                                  ForkJoinWorkerThread))
                                wasInterrupted = true;
                        }
                    }
                    else
                        lock.notifyAll();
                }
            }
        }
    }

    /**
     * Unlocks and sets runState to newRunState.
     * 释放锁并通知所有线程，并且将池状态设置为新状态
     * @param oldRunState a value returned from lockRunState
     * @param newRunState the next value (must have lock bit clear).
     */
    private void unlockRunState(int oldRunState, int newRunState) {
        if (!U.compareAndSwapInt(this, RUNSTATE, oldRunState, newRunState)) {
            Object lock = stealCounter;
            runState = newRunState;              // clears RSIGNAL bit
            if (lock != null)
                synchronized (lock) { lock.notifyAll(); }
        }
    }

    // Creating, registering and deregistering workers

    /**
     * Tries to construct and start one worker. Assumes that total
     * count has already been incremented as a reservation.  Invokes
     * deregisterWorker on any failure.
     *
     *createWorker首先通过线程工厂创一个新的ForkJoinWorkerThread，然后启动这个工作线程（wt.start()）。
     * 如果期间发生异常，调用deregisterWorker处理线程创建失败的逻辑。
     * @return true if successful
     */
    private boolean createWorker() {
        ForkJoinWorkerThreadFactory fac = factory;
        Throwable ex = null;
        ForkJoinWorkerThread wt = null;
        try {
            if (fac != null &&
            /**
             * 此方法为创建工作线程的核心方法：
             * 1、将当前线程池this传入wt中。所以wt中持有当前线程池的引用
             * 2、持有了当前pool引用，wt就可以将自身注入到pool中
             * 3、具体代码(ForkJoinWorkerThread)：
             *         super("aForkJoinWorkerThread");
             *         this.pool = pool;
             *         this.workQueue = pool.registerWorker(this);
             * 4、实现在方法registerWorker,拥有线程的WorkQueue只能出现在数组的奇数下标处
             */

                     (wt = fac.newThread(this)
                ) != null) {
                wt.start();
                return true;
            }
        } catch (Throwable rex) {
            ex = rex;
        }

        deregisterWorker(wt, ex);//线程创建失败处理
        return false;
    }

    /**
     * Tries to add one worker, incrementing ctl counts before doing
     * so, relying on createWorker to back out on failure.
     *
     * @param c incoming ctl value, with total count negative and no
     * idle workers.  On CAS failure, c is refreshed and retried if
     * this holds (otherwise, a new worker is not needed).
     *  尝试添加一个新的工作线程，首先更新ctl中的工作线程数，然后调用createWorker()创建工作线程。
     */
    private void tryAddWorker(long c) {
        boolean add = false;
        do {
            //ctl来记录我们增加的线程, ctl编号-1的16位和编号-2的16位均需要加1,表示active的worker加一，总的worker加一。
            // 成功后我们将调用createWorker。
            long nc = ((AC_MASK & (c + AC_UNIT)) |
                       (TC_MASK & (c + TC_UNIT)));
            if (ctl == c) {
                int rs, stop;                 // check if terminating
                if ((stop = (rs = lockRunState()) & STOP) == 0)//为0表示没有终止
                    add = U.compareAndSwapLong(this, CTL, c, nc);
                unlockRunState(rs, rs & ~RSLOCK); //释放锁
                if (stop != 0)
                    break;
                if (add) {
                    /**
                     * 第一次进入此分支条件为真: 创建工作线程并启动线程
                     */
                    createWorker();
                    break;
                }
            }
        } while (((c = ctl) & ADD_WORKER) != 0L && (int)c == 0);
    }

    /**
     * Callback from ForkJoinWorkerThread constructor to establish and
     * record its WorkQueue.
     * registerWorker是 ForkJoinWorkerThread 构造器的回调函数，用于创建和记录工作线程的 WorkQueue。
     * 1、注意在此为工作线程创建的 WorkQueue 是放在奇数索引的（代码行：i = ((s << 1) | 1) & m;）
     * 2、所以线程 首先,创建一个新的WorkQueue，其次在数组WorkQueue[]寻找奇数下标尚未初始化的位置,
     *      如果循环的次数大于数组长度,还可能需要对数组进行扩容，
     *      然后，设置这个WorkQueue的 config 为 index | mode (下标和模式)
     *      ,scanState为 index (下标>0)。最后启动这个线程
     * @param wt the worker thread
     * @return the worker's queue
     */
    final WorkQueue registerWorker(ForkJoinWorkerThread wt) {
        UncaughtExceptionHandler handler;
        //设置为守护线程
        wt.setDaemon(true);                           // configure thread
        if ((handler = ueh) != null)
            wt.setUncaughtExceptionHandler(handler);
        WorkQueue w = new WorkQueue(this, wt); //构造新的WorkQueue
        int i = 0;                                    // assign a pool index
        int mode = config & MODE_MASK;
        int rs = lockRunState();
        try {
            WorkQueue[] ws; int n;                    // skip if no array
            if ((ws = workQueues) != null && (n = ws.length) > 0) {
                //生成新建WorkQueue的索引
                int s = indexSeed += SEED_INCREMENT;  // unlikely to collide
                int m = n - 1;
                i = ((s << 1) | 1) & m;               // odd-numbered indices  // Worker任务放在奇数索引位 odd-numbered indices
                if (ws[i] != null) {                  // collision 已存在，重新计算索引位
                    int probes = 0;                   // step by approx half n
                    int step = (n <= 4) ? 2 : ((n >>> 1) & EVENMASK) + 2;
                    //查找可用的索引位
                    while (ws[i = (i + step) & m] != null) {
                        if (++probes >= n) {//所有索引位都被占用，对workQueues进行扩容
                            workQueues = ws = Arrays.copyOf(ws, n <<= 1);//workQueues 扩容
                            m = n - 1;
                            probes = 0;
                        }
                    }
                }
                w.hint = s;                           // use as random seed
                w.config = i | mode;
                w.scanState = i;                      // publication fence
                ws[i] = w;
            }
        } finally {
            unlockRunState(rs, rs & ~RSLOCK);
        }
        wt.setName(workerNamePrefix.concat(Integer.toString(i >>> 1)));
        return w;
    }

    /**
     * Final callback from terminating worker, as well as upon failure
     * to construct or start a worker.  Removes record of worker from
     * array, and adjusts counts. If pool is shutting down, tries to
     * complete termination.
     *
     * @param wt the worker thread, or null if construction failed
     * @param ex the exception causing failure, or null if none
     */
    final void deregisterWorker(ForkJoinWorkerThread wt, Throwable ex) {
        WorkQueue w = null;
        if (wt != null && (w = wt.workQueue) != null) {
            WorkQueue[] ws;                           // remove index from array
            int idx = w.config & SMASK;
            int rs = lockRunState();
            if ((ws = workQueues) != null && ws.length > idx && ws[idx] == w)
                ws[idx] = null;
            unlockRunState(rs, rs & ~RSLOCK);
        }
        long c;                                       // decrement counts
        do {} while (!U.compareAndSwapLong
                     (this, CTL, c = ctl, ((AC_MASK & (c - AC_UNIT)) |
                                           (TC_MASK & (c - TC_UNIT)) |
                                           (SP_MASK & c))));
        if (w != null) {
            w.qlock = -1;                             // ensure set
            w.transferStealCount(this);
            w.cancelAll();                            // cancel remaining tasks
        }
        for (;;) {                                    // possibly replace
            WorkQueue[] ws; int m, sp;
            if (tryTerminate(false, false) || w == null || w.array == null ||
                (runState & STOP) != 0 || (ws = workQueues) == null ||
                (m = ws.length - 1) < 0)              // already terminating
                break;
            if ((sp = (int)(c = ctl)) != 0) {         // wake up replacement
                if (tryRelease(c, ws[sp & m], AC_UNIT))
                    break;
            }
            else if (ex != null && (c & ADD_WORKER) != 0L) {
                tryAddWorker(c);                      // create replacement
                break;
            }
            else                                      // don't need replacement
                break;
        }
        if (ex == null)                               // help clean on way out
            ForkJoinTask.helpExpungeStaleExceptions();
        else                                          // rethrow
            ForkJoinTask.rethrow(ex);
    }

    // Signalling

    /**
     * Tries to create or activate a worker if too few are active.
     *
     * @param ws the worker array to use to find signallees
     * @param q a WorkQueue --if non-null, don't retry if now empty
     *          新建或唤醒一个工作线程，在externalPush、externalSubmit、workQueue.push、scan中调用。
     *          如果还有空闲线程，则尝试唤醒索引到的 WorkQueue 的parker线程；如果工作线程过少（(ctl & ADD_WORKER) != 0L），
     *          则调用tryAddWorker添加一个新的工作线程。
     */
    final void signalWork(WorkQueue[] ws, WorkQueue q) {
        long c;
        int sp,
                i;
        WorkQueue v;
        Thread p;
        /**
         * 又是一个递归通过改变状态来进入不同分支的流程设计模式
         */
        while ((c = ctl) < 0L) {                       // too few active:  第一次进入此分支条件为真: ctl小于0表示活动线程较少还没超过阀值，直接拿过来用即可。
            //ctl<0意味着active的线程还没有到达阈值，只有ctl>0我们才会去讨论要不要创建或者激活新的线程。
            // 此处通过强转(int)ctl，很巧妙的拿到了ctl的低16位
            if ((sp = (int)c) == 0) {                  //no idle workers 没有空闲线程: 第一次进入此分支条件为真: ctl代表的是idle worker当低16位为0的时候，意味着此刻没有已经启动但是空闲的线程,如果在没有空闲的线程的情况下
                //(c & ADD_WORKER) != 0L 表明(c & ADD_WORKER) < 0,即使再加1个线程，最多也就=0，不会大于0。超出阀值。
                if ((c & ADD_WORKER) != 0L)            //  too few workers： 第一次进入此分支条件为真: 工作线程太少，创建建工作线程
                /**
                 * 线程创建并启动的核心方法入口：创建线程并启动线程
                 */
                    tryAddWorker(c);//工作线程太少，添加新的工作线程
                break;
            }
            //下面分支均为(sp = (int)c) != 0的情况,即还有idle worker,我们只需要active其中的一个即可.不需要新加
            if (ws == null)                            // unstarted/terminated
                break;
            if (ws.length <= (i = sp & SMASK))         // terminated
                break;
            if ((v = ws[i]) == null)                   // terminating
                break;
            //计算ctl，加上版本戳SS_SEQ避免ABA问题
            int vs = (sp + SS_SEQ) & ~INACTIVE;        // next scanState
            int d = sp - v.scanState;                  // screen CAS
            //计算活跃线程数（高32位）并更新为下一个栈顶的scanState（低32位）
            long nc = (UC_MASK & (c + AC_UNIT)) | (SP_MASK & v.stackPred);
            if (d == 0 && U.compareAndSwapLong(this, CTL, c, nc)) {
                v.scanState = vs;                      // activate v
                if ((p = v.parker) != null)
                    U.unpark(p);//唤醒阻塞线程
                break;
            }
            if (q != null && q.base == q.top)          // no more work
                break;
        }
    }

    /**
     * Signals and releases worker v if it is top of idle worker
     * stack.  This performs a one-shot version of signalWork only if
     * there is (apparently) at least one idle worker.
     *
     * @param c incoming ctl value
     * @param v if non-null, a worker
     * @param inc the increment to active count (zero when compensating)
     * @return true if successful
     */
    private boolean tryRelease(long c, WorkQueue v, long inc) {
        int sp = (int)c, vs = (sp + SS_SEQ) & ~INACTIVE; Thread p;
        if (v != null && v.scanState == sp) {          // v is at top of stack
            long nc = (UC_MASK & (c + inc)) | (SP_MASK & v.stackPred);
            if (U.compareAndSwapLong(this, CTL, c, nc)) {
                v.scanState = vs;
                if ((p = v.parker) != null)
                    U.unpark(p);
                return true;
            }
        }
        return false;
    }

    // Scanning for tasks

    /**
     * Top-level runloop for workers, called by ForkJoinWorkerThread.run.
     *
     * 1、在ForkJoinWorkerThread中调用此方法
     * 2、在ForkJoinWorkerThread启动之后会调用pool的runWorker来获取任务执行。
     * @param w 为一个工作队列，将工作队列传入让pool完成其执行
     */
    final void runWorker(WorkQueue w) {
        //根据实际情况扩容
        w.growArray();                   // allocate queue
        int seed = w.hint;               // initially holds randomization hint
        int r = (seed == 0) ? 1 : seed;  // avoid 0 for xorShift
        //自旋执行任务
        for (ForkJoinTask<?> t;;) {
            /**
             *  1、调用scan尝试去偷取一个任务,如果偷取成功然后调用runTask
             *      如果偷取失败则调用awaitWork，这里的scan是框架的重要的实现
             *  2、
             */
            if ((t = scan(w, r)) != null)
                w.runTask(t);
            else if (!awaitWork(w, r))
                break;
            r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // xorshift
        }
    }

    /**
     * Scans for and tries to steal a top-level task. Scans start at a
     * random location, randomly moving on apparent contention,
     * otherwise continuing linearly until reaching two consecutive
     * empty passes over all queues with the same checksum (summing
     * each base index of each queue, that moves on each steal), at
     * which point the worker tries to inactivate and then re-scans,
     * attempting to re-activate (itself or some other worker) if
     * finding a task; otherwise returning null to await work.  Scans
     * otherwise touch as little memory as possible, to reduce
     * disruption on other scanning threads.
     *
     * @param w the worker (via its WorkQueue)
     * @param r a random seed
     * @return a task, or null if none found
     *  一、WorkQueue是有owner线程的队列，我们可以知道以下信息:
     *      1、config = index | mode
     *      2、scanState = index > 0
     *  二、我们首先通过random的r来找到一个我们准备偷取的队列。
     *      1、如果我们准备偷取的队列刚好有任务在排队(也有可能是owner自己的那个队列)；从队列的队尾即base位置取到任务返回base + 1
     *      2、如果我们遍历了一圈(((k = (k + 1) & m) == origin))都没有偷到,我们就认为当前的active 线程过剩了,我们准备将当前的线程
     *          (即owner)挂起,我们首先 index | INACTIVE 形成 ctl的后32位;并行将ac减一。
     *          其次，将原来的挂起的top的index记录到stackPred中。
     *      3、继续遍历如果仍然一无所获,将跳出循环；如果偷到了一个任务,我们将使用tryRelease激活。
     */
    private ForkJoinTask<?> scan(WorkQueue w, int r) {
        WorkQueue[] ws;
        int m;
        if ((ws = workQueues) != null //队列不为空
                && (m = ws.length - 1) > 0 //队列长度大于1
                && w != null) {//队列不为空
            int ss = w.scanState;                     // initially non-negative
            //自旋递归
            for (int origin = r & m, k = origin, oldSum = 0, checkSum = 0;;) {
                WorkQueue q;
                ForkJoinTask<?>[] a;
                ForkJoinTask<?> t;
                int b, n; long c;
                if ((q = ws[k]) != null) {
                    if ((n = (b = q.base) - q.top) < 0 &&
                        (a = q.array) != null) {      // non-empty
                        long i = (((a.length - 1) & b) << ASHIFT) + ABASE;
                        if ((t = ((ForkJoinTask<?>)
                                  U.getObjectVolatile(a, i))) != null &&
                            q.base == b) {
                            if (ss >= 0) {
                                if (U.compareAndSwapObject(a, i, t, null)) {
                                    q.base = b + 1;
                                    if (n < -1)       // signal others
                                        signalWork(ws, q);
                                    return t;
                                }
                            }
                            else if (oldSum == 0 &&   // try to activate
                                     w.scanState < 0)
                                tryRelease(c = ctl, ws[m & (int)c], AC_UNIT);
                        }
                        if (ss < 0)                   // refresh
                            ss = w.scanState;
                        r ^= r << 1; r ^= r >>> 3; r ^= r << 10;
                        origin = k = r & m;           // move and rescan
                        oldSum = checkSum = 0;
                        continue;
                    }
                    checkSum += b;
                }
                if ((k = (k + 1) & m) == origin) {    // continue until stable
                    if ((ss >= 0 || (ss == (ss = w.scanState))) &&
                        oldSum == (oldSum = checkSum)) {
                        if (ss < 0 || w.qlock < 0)    // already inactive
                            break;
                        int ns = ss | INACTIVE;       // try to inactivate
                        long nc = ((SP_MASK & ns) |
                                   (UC_MASK & ((c = ctl) - AC_UNIT)));
                        w.stackPred = (int)c;         // hold prev stack top
                        U.putInt(w, QSCANSTATE, ns);
                        if (U.compareAndSwapLong(this, CTL, c, nc))
                            ss = ns;
                        else
                            w.scanState = ss;         // back out
                    }
                    checkSum = 0;
                }
            }
        }
        return null;
    }

    /**
     * Possibly blocks worker w waiting for a task to steal, or
     * returns false if the worker should terminate.  If inactivating
     * w has caused the pool to become quiescent, checks for pool
     * termination, and, so long as this is not the only worker, waits
     * for up to a given duration.  On timeout, if ctl has not
     * changed, terminates the worker, which will in turn wake up
     * another worker to possibly repeat this process.
     *
     * @param w the calling worker
     * @param r a random seed (for spins)
     * @return false if the worker should terminate
     */
    private boolean awaitWork(WorkQueue w, int r) {
        if (w == null || w.qlock < 0)                 // w is terminating
            return false;
        for (int pred = w.stackPred, spins = SPINS, ss;;) {
            if ((ss = w.scanState) >= 0)
                break;
            else if (spins > 0) {
                r ^= r << 6; r ^= r >>> 21; r ^= r << 7;
                if (r >= 0 && --spins == 0) {         // randomize spins
                    WorkQueue v; WorkQueue[] ws; int s, j; AtomicLong sc;
                    if (pred != 0 && (ws = workQueues) != null &&
                        (j = pred & SMASK) < ws.length &&
                        (v = ws[j]) != null &&        // see if pred parking
                        (v.parker == null || v.scanState >= 0))
                        spins = SPINS;                // continue spinning
                }
            }
            else if (w.qlock < 0)                     // recheck after spins
                return false;
            else if (!Thread.interrupted()) {
                long c, prevctl, parkTime, deadline;
                int ac = (int)((c = ctl) >> AC_SHIFT) + (config & SMASK);
                if ((ac <= 0 && tryTerminate(false, false)) ||
                    (runState & STOP) != 0)           // pool terminating
                    return false;
                if (ac <= 0 && ss == (int)c) {        // is last waiter
                    prevctl = (UC_MASK & (c + AC_UNIT)) | (SP_MASK & pred);
                    int t = (short)(c >>> TC_SHIFT);  // shrink excess spares
                    if (t > 2 && U.compareAndSwapLong(this, CTL, c, prevctl))
                        return false;                 // else use timed wait
                    parkTime = IDLE_TIMEOUT * ((t >= 0) ? 1 : 1 - t);
                    deadline = System.nanoTime() + parkTime - TIMEOUT_SLOP;
                }
                else
                    prevctl = parkTime = deadline = 0L;
                Thread wt = Thread.currentThread();
                U.putObject(wt, PARKBLOCKER, this);   // emulate LockSupport
                w.parker = wt;
                if (w.scanState < 0 && ctl == c)      // recheck before park
                    U.park(false, parkTime);
                U.putOrderedObject(w, QPARKER, null);
                U.putObject(wt, PARKBLOCKER, null);
                if (w.scanState >= 0)
                    break;
                if (parkTime != 0L && ctl == c &&
                    deadline - System.nanoTime() <= 0L &&
                    U.compareAndSwapLong(this, CTL, c, prevctl))
                    return false;                     // shrink pool
            }
        }
        return true;
    }

    // Joining tasks

    /**
     * Tries to steal and run tasks within the target's computation.
     * Uses a variant of the top-level algorithm, restricted to tasks
     * with the given task as ancestor: It prefers taking and running
     * eligible tasks popped from the worker's own queue (via
     * popCC). Otherwise it scans others, randomly moving on
     * contention or execution, deciding to give up based on a
     * checksum (via return codes frob pollAndExecCC). The maxTasks
     * argument supports external usages; internal calls use zero,
     * allowing unbounded steps (external calls trap non-positive
     * values).
     *
     * @param w caller
     * @param maxTasks if non-zero, the maximum number of other tasks to run
     * @return task status on exit
     */
    final int helpComplete(WorkQueue w, CountedCompleter<?> task,
                           int maxTasks) {
        WorkQueue[] ws; int s = 0, m;
        if ((ws = workQueues) != null && (m = ws.length - 1) >= 0 &&
            task != null && w != null) {
            int mode = w.config;                 // for popCC
            int r = w.hint ^ w.top;              // arbitrary seed for origin
            int origin = r & m;                  // first queue to scan
            int h = 1;                           // 1:ran, >1:contended, <0:hash
            for (int k = origin, oldSum = 0, checkSum = 0;;) {
                CountedCompleter<?> p; WorkQueue q;
                if ((s = task.status) < 0)
                    break;
                if (h == 1 && (p = w.popCC(task, mode)) != null) {
                    p.doExec();                  // run local task
                    if (maxTasks != 0 && --maxTasks == 0)
                        break;
                    origin = k;                  // reset
                    oldSum = checkSum = 0;
                }
                else {                           // poll other queues
                    if ((q = ws[k]) == null)
                        h = 0;
                    else if ((h = q.pollAndExecCC(task)) < 0)
                        checkSum += h;
                    if (h > 0) {
                        if (h == 1 && maxTasks != 0 && --maxTasks == 0)
                            break;
                        r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // xorshift
                        origin = k = r & m;      // move and restart
                        oldSum = checkSum = 0;
                    }
                    else if ((k = (k + 1) & m) == origin) {
                        if (oldSum == (oldSum = checkSum))
                            break;
                        checkSum = 0;
                    }
                }
            }
        }
        return s;
    }

    /**
     * Tries to locate and execute tasks for a stealer of the given
     * task, or in turn one of its stealers, Traces currentSteal ->
     * currentJoin links looking for a thread working on a descendant
     * of the given task and with a non-empty queue to steal back and
     * execute tasks from. The first call to this method upon a
     * waiting join will often entail scanning/search, (which is OK
     * because the joiner has nothing better to do), but this method
     * leaves hints in workers to speed up subsequent calls.
     *
     * @param w caller
     * @param task the task to join
     */
    private void helpStealer(WorkQueue w, ForkJoinTask<?> task) {
        WorkQueue[] ws = workQueues;
        int oldSum = 0, checkSum, m;
        if (ws != null && (m = ws.length - 1) >= 0 && w != null &&
            task != null) {
            do {                                       // restart point
                checkSum = 0;                          // for stability check
                ForkJoinTask<?> subtask;
                WorkQueue j = w, v;                    // v is subtask stealer
                descent: for (subtask = task; subtask.status >= 0; ) {
                    for (int h = j.hint | 1, k = 0, i; ; k += 2) {
                        if (k > m)                     // can't find stealer
                            break descent;
                        if ((v = ws[i = (h + k) & m]) != null) {
                            if (v.currentSteal == subtask) {
                                j.hint = i;
                                break;
                            }
                            checkSum += v.base;
                        }
                    }
                    for (;;) {                         // help v or descend
                        ForkJoinTask<?>[] a; int b;
                        checkSum += (b = v.base);
                        ForkJoinTask<?> next = v.currentJoin;
                        if (subtask.status < 0 || j.currentJoin != subtask ||
                            v.currentSteal != subtask) // stale
                            break descent;
                        if (b - v.top >= 0 || (a = v.array) == null) {
                            if ((subtask = next) == null)
                                break descent;
                            j = v;
                            break;
                        }
                        int i = (((a.length - 1) & b) << ASHIFT) + ABASE;
                        ForkJoinTask<?> t = ((ForkJoinTask<?>)
                                             U.getObjectVolatile(a, i));
                        if (v.base == b) {
                            if (t == null)             // stale
                                break descent;
                            if (U.compareAndSwapObject(a, i, t, null)) {
                                v.base = b + 1;
                                ForkJoinTask<?> ps = w.currentSteal;
                                int top = w.top;
                                do {
                                    U.putOrderedObject(w, QCURRENTSTEAL, t);
                                    t.doExec();        // clear local tasks too
                                } while (task.status >= 0 &&
                                         w.top != top &&
                                         (t = w.pop()) != null);
                                U.putOrderedObject(w, QCURRENTSTEAL, ps);
                                if (w.base != w.top)
                                    return;            // can't further help
                            }
                        }
                    }
                }
            } while (task.status >= 0 && oldSum != (oldSum = checkSum));
        }
    }

    /**
     * Tries to decrement active count (sometimes implicitly) and
     * possibly release or create a compensating worker in preparation
     * for blocking. Returns false (retryable by caller), on
     * contention, detected staleness, instability, or termination.
     *
     * @param w caller
     */
    private boolean tryCompensate(WorkQueue w) {
        boolean canBlock;
        WorkQueue[] ws; long c; int m, pc, sp;
        if (w == null || w.qlock < 0 ||           // caller terminating
            (ws = workQueues) == null || (m = ws.length - 1) <= 0 ||
            (pc = config & SMASK) == 0)           // parallelism disabled
            canBlock = false;
        else if ((sp = (int)(c = ctl)) != 0)      // release idle worker
            canBlock = tryRelease(c, ws[sp & m], 0L);
        else {
            int ac = (int)(c >> AC_SHIFT) + pc;
            int tc = (short)(c >> TC_SHIFT) + pc;
            int nbusy = 0;                        // validate saturation
            for (int i = 0; i <= m; ++i) {        // two passes of odd indices
                WorkQueue v;
                if ((v = ws[((i << 1) | 1) & m]) != null) {
                    if ((v.scanState & SCANNING) != 0)
                        break;
                    ++nbusy;
                }
            }
            if (nbusy != (tc << 1) || ctl != c)
                canBlock = false;                 // unstable or stale
            else if (tc >= pc && ac > 1 && w.isEmpty()) {
                long nc = ((AC_MASK & (c - AC_UNIT)) |
                           (~AC_MASK & c));       // uncompensated
                canBlock = U.compareAndSwapLong(this, CTL, c, nc);
            }
            else if (tc >= MAX_CAP ||
                     (this == common && tc >= pc + commonMaxSpares))
                throw new RejectedExecutionException(
                    "Thread limit exceeded replacing blocked worker");
            else {                                // similar to tryAddWorker
                boolean add = false; int rs;      // CAS within lock
                long nc = ((AC_MASK & c) |
                           (TC_MASK & (c + TC_UNIT)));
                if (((rs = lockRunState()) & STOP) == 0)
                    add = U.compareAndSwapLong(this, CTL, c, nc);
                unlockRunState(rs, rs & ~RSLOCK);
                canBlock = add && createWorker(); // throws on exception
            }
        }
        return canBlock;
    }

    /**
     * Helps and/or blocks until the given task is done or timeout.
     * 帮助完成任务或者阻塞直到给任务的已经完成或者超时
     * 在ForkJoinTask.join()时被调用。
     * @param w caller
     * @param task the task
     * @param deadline for timed waits, if nonzero
     * @return task status on exit
     * 说明： 如果当前 join 任务不在Worker等待队列的top位，或者任务执行失败，调用此方法来帮助执行或阻塞当前 join 的任务。函数执行流程如下：
     *
     * 由于每次调用awaitJoin都会优先执行当前join的任务，所以首先会更新currentJoin为当前join任务；
     * 进入自旋：
     * 首先检查任务是否已经完成（通过task.status < 0判断），如果给定任务执行完毕|取消|异常 则跳出循环返回执行状态s；
     * 如果是 CountedCompleter 任务类型，调用helpComplete方法来完成join操作（后面笔者会开新篇来专门讲解CountedCompleter，本篇暂时不做详细解析）；
     * 非 CountedCompleter 任务类型调用WorkQueue.tryRemoveAndExec尝试执行任务；
     * 如果给定 WorkQueue 的等待队列为空或任务执行失败，说明任务可能被偷，调用helpStealer帮助偷取者执行任务（也就是说，偷取者帮我执行任务，我去帮偷取者执行它的任务）；
     * 再次判断任务是否执行完毕（task.status < 0），如果任务执行失败，计算一个等待时间准备进行补偿操作；
     * 调用tryCompensate方法为给定 WorkQueue 尝试执行补偿操作。在执行补偿期间，如果发现 资源争用|池处于unstable状态|当前Worker已终止，
     * 则调用ForkJoinTask.internalWait()方法等待指定的时间，任务唤醒之后继续自旋
     */
    final int awaitJoin(WorkQueue w, ForkJoinTask<?> task, long deadline) {
        int s = 0;
        if (task != null && w != null) { //队列和任务不为空
            //将当前join设置为prevJoin,然后将入参task替换为当前Join
            ForkJoinTask<?> prevJoin = w.currentJoin;
            U.putOrderedObject(w, QCURRENTJOIN, task);
            //判断是否为CountedCompleter类型的任务
            CountedCompleter<?> cc = (task instanceof CountedCompleter) ?
                (CountedCompleter<?>)task : null;
            //递归处理
            for (;;) {
                if ((s = task.status) < 0)//已经完成|取消|异常 跳出循环
                    break;
                if (cc != null)//CountedCompleter任务由helpComplete来完成join
                    helpComplete(w, cc, 0);
                else if (w.base == w.top || w.tryRemoveAndExec(task))
                    //队列为空或执行失败，任务可能被偷，帮助偷取者执行该任务
                    helpStealer(w, task);
                if ((s = task.status) < 0)//窃取任务完成后，再次判断任务是否已经完成，如果小于0表示已经完成，直接结束
                    break;
                long ms, ns;
                if (deadline == 0L)
                    ms = 0L;
                else if ((ns = deadline - System.nanoTime()) <= 0L)//如果上面均未完成，则判断是否超时
                    break;
                else if ((ms = TimeUnit.NANOSECONDS.toMillis(ns)) <= 0L)
                    ms = 1L;
                if (tryCompensate(w)) {//执行补偿操作
                    task.internalWait(ms);//补偿执行成功，任务等待指定时间
                    U.getAndAddLong(this, CTL, AC_UNIT);//更新活跃线程数
                }
            }
            U.putOrderedObject(w, QCURRENTJOIN, prevJoin);//循环结束，替换为原来的join任务
        }
        return s;
    }

    // Specialized scanning

    /**
     * Returns a (probably) non-empty steal queue, if one is found
     * during a scan, else null.  This method must be retried by
     * caller if, by the time it tries to use the queue, it is empty.
     */
    private WorkQueue findNonEmptyStealQueue() {
        WorkQueue[] ws; int m;  // one-shot version of scan loop
        int r = ThreadLocalRandom.nextSecondarySeed();
        if ((ws = workQueues) != null && (m = ws.length - 1) >= 0) {
            for (int origin = r & m, k = origin, oldSum = 0, checkSum = 0;;) {
                WorkQueue q; int b;
                if ((q = ws[k]) != null) {
                    if ((b = q.base) - q.top < 0)
                        return q;
                    checkSum += b;
                }
                if ((k = (k + 1) & m) == origin) {
                    if (oldSum == (oldSum = checkSum))
                        break;
                    checkSum = 0;
                }
            }
        }
        return null;
    }

    /**
     * Runs tasks until {@code isQuiescent()}. We piggyback on
     * active count ctl maintenance, but rather than blocking
     * when tasks cannot be found, we rescan until all others cannot
     * find tasks either.
     */
    final void helpQuiescePool(WorkQueue w) {
        ForkJoinTask<?> ps = w.currentSteal; // save context
        for (boolean active = true;;) {
            long c; WorkQueue q; ForkJoinTask<?> t; int b;
            w.execLocalTasks();     // run locals before each scan
            if ((q = findNonEmptyStealQueue()) != null) {
                if (!active) {      // re-establish active count
                    active = true;
                    U.getAndAddLong(this, CTL, AC_UNIT);
                }
                if ((b = q.base) - q.top < 0 && (t = q.pollAt(b)) != null) {
                    U.putOrderedObject(w, QCURRENTSTEAL, t);
                    t.doExec();
                    if (++w.nsteals < 0)
                        w.transferStealCount(this);
                }
            }
            else if (active) {      // decrement active count without queuing
                long nc = (AC_MASK & ((c = ctl) - AC_UNIT)) | (~AC_MASK & c);
                if ((int)(nc >> AC_SHIFT) + (config & SMASK) <= 0)
                    break;          // bypass decrement-then-increment
                if (U.compareAndSwapLong(this, CTL, c, nc))
                    active = false;
            }
            else if ((int)((c = ctl) >> AC_SHIFT) + (config & SMASK) <= 0 &&
                     U.compareAndSwapLong(this, CTL, c, c + AC_UNIT))
                break;
        }
        U.putOrderedObject(w, QCURRENTSTEAL, ps);
    }

    /**
     * Gets and removes a local or stolen task for the given worker.
     *
     * @return a task, if available
     *  试图从指定的队列中取出下一个待执行任务
     */
    final ForkJoinTask<?> nextTaskFor(WorkQueue w) {
        for (ForkJoinTask<?> t;;) {
            WorkQueue q; int b;
            // 该方法试图从“w”这个队列获取下一个待处理子任务
            if ((t = w.nextLocalTask()) != null)
                return t;
            // 如果没有获取到，则使用findNonEmptyStealQueue方法
            // 随机得到一个元素非空，并且可以进行任务窃取的存在于ForkJoinPool中的其它队列
            // 这个队列被记为“q”
            if ((q = findNonEmptyStealQueue()) == null)
                return null;
            // 试图从“q”这个队列base位处取出待执行任务
            if ((b = q.base) - q.top < 0 && (t = q.pollAt(b)) != null)
                return t;
        }
    }

    /**
     * Returns a cheap heuristic guide for task partitioning when
     * programmers, frameworks, tools, or languages have little or no
     * idea about task granularity.  In essence, by offering this
     * method, we ask users only about tradeoffs in overhead vs
     * expected throughput and its variance, rather than how finely to
     * partition tasks.
     *
     * In a steady state strict (tree-structured) computation, each
     * thread makes available for stealing enough tasks for other
     * threads to remain active. Inductively, if all threads play by
     * the same rules, each thread should make available only a
     * constant number of tasks.
     *
     * The minimum useful constant is just 1. But using a value of 1
     * would require immediate replenishment upon each steal to
     * maintain enough tasks, which is infeasible.  Further,
     * partitionings/granularities of offered tasks should minimize
     * steal rates, which in general means that threads nearer the top
     * of computation tree should generate more than those nearer the
     * bottom. In perfect steady state, each thread is at
     * approximately the same level of computation tree. However,
     * producing extra tasks amortizes the uncertainty of progress and
     * diffusion assumptions.
     *
     * So, users will want to use values larger (but not much larger)
     * than 1 to both smooth over transient shortages and hedge
     * against uneven progress; as traded off against the cost of
     * extra task overhead. We leave the user to pick a threshold
     * value to compare with the results of this call to guide
     * decisions, but recommend values such as 3.
     *
     * When all threads are active, it is on average OK to estimate
     * surplus strictly locally. In steady-state, if one thread is
     * maintaining say 2 surplus tasks, then so are others. So we can
     * just use estimated queue length.  However, this strategy alone
     * leads to serious mis-estimates in some non-steady-state
     * conditions (ramp-up, ramp-down, other stalls). We can detect
     * many of these by further considering the number of "idle"
     * threads, that are known to have zero queued tasks, so
     * compensate by a factor of (#idle/#active) threads.
     */
    static int getSurplusQueuedTaskCount() {
        Thread t; ForkJoinWorkerThread wt; ForkJoinPool pool; WorkQueue q;
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)) {
            int p = (pool = (wt = (ForkJoinWorkerThread)t).pool).
                config & SMASK;
            int n = (q = wt.workQueue).top - q.base;
            int a = (int)(pool.ctl >> AC_SHIFT) + p;
            return n - (a > (p >>>= 1) ? 0 :
                        a > (p >>>= 1) ? 1 :
                        a > (p >>>= 1) ? 2 :
                        a > (p >>>= 1) ? 4 :
                        8);
        }
        return 0;
    }

    //  Termination

    /**
     * Possibly initiates and/or completes termination.
     *
     * @param now if true, unconditionally terminate, else only
     * if no work and no active workers
     * @param enable if true, enable shutdown when next possible
     * @return true if now terminating or terminated
     */
    private boolean tryTerminate(boolean now, boolean enable) {
        int rs;
        if (this == common)                       // cannot shut down
            return false;
        if ((rs = runState) >= 0) {
            if (!enable)
                return false;
            rs = lockRunState();                  // enter SHUTDOWN phase
            unlockRunState(rs, (rs & ~RSLOCK) | SHUTDOWN);
        }

        if ((rs & STOP) == 0) {
            if (!now) {                           // check quiescence
                for (long oldSum = 0L;;) {        // repeat until stable
                    WorkQueue[] ws; WorkQueue w; int m, b; long c;
                    long checkSum = ctl;
                    if ((int)(checkSum >> AC_SHIFT) + (config & SMASK) > 0)
                        return false;             // still active workers
                    if ((ws = workQueues) == null || (m = ws.length - 1) <= 0)
                        break;                    // check queues
                    for (int i = 0; i <= m; ++i) {
                        if ((w = ws[i]) != null) {
                            if ((b = w.base) != w.top || w.scanState >= 0 ||
                                w.currentSteal != null) {
                                tryRelease(c = ctl, ws[m & (int)c], AC_UNIT);
                                return false;     // arrange for recheck
                            }
                            checkSum += b;
                            if ((i & 1) == 0)
                                w.qlock = -1;     // try to disable external
                        }
                    }
                    if (oldSum == (oldSum = checkSum))
                        break;
                }
            }
            if ((runState & STOP) == 0) {
                rs = lockRunState();              // enter STOP phase
                unlockRunState(rs, (rs & ~RSLOCK) | STOP);
            }
        }

        int pass = 0;                             // 3 passes to help terminate
        for (long oldSum = 0L;;) {                // or until done or stable
            WorkQueue[] ws; WorkQueue w; ForkJoinWorkerThread wt; int m;
            long checkSum = ctl;
            if ((short)(checkSum >>> TC_SHIFT) + (config & SMASK) <= 0 ||
                (ws = workQueues) == null || (m = ws.length - 1) <= 0) {
                if ((runState & TERMINATED) == 0) {
                    rs = lockRunState();          // done
                    unlockRunState(rs, (rs & ~RSLOCK) | TERMINATED);
                    synchronized (this) { notifyAll(); } // for awaitTermination
                }
                break;
            }
            for (int i = 0; i <= m; ++i) {
                if ((w = ws[i]) != null) {
                    checkSum += w.base;
                    w.qlock = -1;                 // try to disable
                    if (pass > 0) {
                        w.cancelAll();            // clear queue
                        if (pass > 1 && (wt = w.owner) != null) {
                            if (!wt.isInterrupted()) {
                                try {             // unblock join
                                    wt.interrupt();
                                } catch (Throwable ignore) {
                                }
                            }
                            if (w.scanState < 0)
                                U.unpark(wt);     // wake up
                        }
                    }
                }
            }
            if (checkSum != oldSum) {             // unstable
                oldSum = checkSum;
                pass = 0;
            }
            else if (pass > 3 && pass > m)        // can't further help
                break;
            else if (++pass > 1) {                // try to dequeue
                long c; int j = 0, sp;            // bound attempts
                while (j++ <= m && (sp = (int)(c = ctl)) != 0)
                    tryRelease(c, ws[sp & m], AC_UNIT);
            }
        }
        return true;
    }

    // External operations

    /**
     * Full version of externalPush, handling uncommon cases, as well
     * as performing secondary initialization upon the first
     * submission of the first task to the pool.  It also detects
     * first submission by an external thread and creates a new shared
     * queue if the one at index if empty or contended.
     *
     * @param task the task. Caller must ensure non-null.
     * 进行WorkQueue数组初始化的代码：
     *              当外部调用这通过submit、execute、invoke方法向ForkJoinPool提交一个计算任务时，
     *              会运行这段代码为ForkJoinPool创建多个WorkQueue并形成数组。
     * 任务提交:
     * 1、externalSubmit是externalPush的完整版本，主要用于第一次提交任务时初始化workQueues及相关属性，并且提交给定任务到队列中。具体执行步骤如下：
     * 2、如果池为终止状态(runState<0)，调用tryTerminate来终止线程池，并抛出任务拒绝异常；
     * 3、 如果尚未初始化，就为 FJP 执行初始化操作：初始化stealCounter、创建workerQueues，然后继续自旋；
     * 4、初始化完成后，执行在externalPush中相同的操作：获取 workQueue，放入指定任务。任务提交成功后调用signalWork方法创建或激活线程；
     * 5、如果在步骤3中获取到的 workQueue 为null，会在这一步中创建一个 workQueue，创建成功继续自旋执行第三步操作；
     * 6、如果非上述情况，或者有线程争用资源导致获取锁失败，就重新获取线程探针值继续自旋。
     *
     */
    private void externalSubmit(ForkJoinTask<?> task) {
        //初始化调用线程的探针值，用于计算WorkQueue索引
        int r;                                    // initialize caller's probe:此什第一次进入为0，初始化之后不会改变
        if ((r = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();
            r = ThreadLocalRandom.getProbe();
        }
        /**
         * 设计思路：
         *  通过for递归循环，不断改变相应条件，从而进入不同分支处理。
         *  第一遍循环: (runState不是开始状态):
         *              1.lock;
         *              2.创建数组WorkQueue[n]，这里的n是power of 2;
         *              3. runState设置为开始状态。
         *  第二遍循环:(根据ThreadLocalRandom.getProbe()hash后的数组中相应位置的WorkQueue未初始化):
         *              初始化WorkQueue,通过这种方式创立的WorkQueue均是SHARED_QUEUE,scanState为INACTIVE
         *  第三遍循环: 找到刚刚创建的WorkQueue,lock住队列,将数据塞到arraytop位置。如果添加成功，
         *              就用调用接下来要摊开讲的重要的方法signalWork。
         */
        for (;;) {
            WorkQueue[] ws;
            WorkQueue q;
            int rs,//runState
                    m,//ws.length - 1
                    k;
            boolean move = false;
            // for递归第一次：runState = 0
            // for递归第二次：runState = 4
            if ((rs = runState) < 0) {/**CASE1：线程池已经关闭，则执行终止操作，并拒绝该任务的提交；*/
                // runState <0 表示SHUTDOWN
                tryTerminate(false, false);     // help terminate尝试终止操作
                throw new RejectedExecutionException();
            }
            //如果条件成立，就说明当前ForkJoinPool类中，还没有任何队列，所以要进行队列初始化
            //for递归第一次：(rs & STARTED) == 0 为真，即表示还未启动，需要初始化，进行初始化
            //for递归第二次：已经完成初始化，下面条件均不成功，所以不会进入此分支
            /** CASE2: 线程池未初始化，则进行初始化，主要就是初始化任务队列数组；*/
            else if ((rs & STARTED) == 0 ||     // initialize 如果不是初始状态，则进行初始化
                     ((ws = workQueues) == null || //或者工作队形为Null
                             (m = ws.length - 1) < 0)) {//或者工作队列长度为0
                int ns = 0;
                rs = lockRunState();//初始化之前给线程池状态加锁：rs  = 1
                try {
                    //取得锁之后进行初始化
                    if ((rs & STARTED) == 0) { //rs & STARTED 相当于 1 & STARTED，为0，初次会进入此条件
                        //初始化stealCounter（0）
                        U.compareAndSwapObject(this, STEALCOUNTER, null,
                                               new AtomicLong());
                        // create workQueues array with size a power of two
                        //创建workQueues，容量为2的幂次方
                        /**
                         * 1、这是config的来源,mode是ForkJoinPool构造函数中设定的asyncMode，如果为LIFO，则mode为0，否则为65536
                         * 2、parallelism 为技术人员设置的（或者程序自行设定的）并发等级
                         * 3、MASK = 0xffff =  0b11111111_11111111 = 2的16次方
                         * 4、config = 0x0004 = 0b00000000_00000100
                         * 5、parallelism与n的关系图(即n的容量是位数递增的)
                         * parallelism	n	parallelism	n
                         * 1	4	2	4
                         * 3	8	4	8
                         * 5	16	6	16
                         * 7	16	8	16
                         * 9	32	10	32
                         * …	32	14	32
                         * …	32	16	32
                         * 17	64	…	…
                         */
                        // 初始化工作队列数组, 数组大小必须为2的幂次

                        int p = config & SMASK; // ensure at least 2 slots。与之后的p = config = 0x0004
                        int n = (p > 1) ? p - 1 : 1;// 初始化时：n = p - 1 = 4 -1 = 3 = 0b00000000_00000011
                        // n >>> 1 ----> 0b00000000_00000011 >>> 1 =  0b00000000_00000001
                        // n |= n >>>1 ---->0b00000000_00000011 | 0b00000000_00000001 = 0b00000000_00000011
                        n |= n >>> 1;
                        n |= n >>> 2;
                        n |= n >>> 4;
                        n |= n >>> 8;
                        n |= n >>> 16;
                        n = (n + 1) << 1;
                        //初始化workQueues
                        workQueues = new WorkQueue[n];
                        ns = STARTED;   // 线程池状态转化为STARTED
                    }
                } finally {
                    //释放锁
                    unlockRunState(rs, (rs & ~RSLOCK) | ns);//解锁并更新runState
                }
            }
            //第二次循环时：r为线程的一个标识（初生成后不会改变）,m = 7 =0b0111,SQMASK = 0b01111110、此时ws[k = r & m & SQMASK] = null,此条件不成立
            //在第三次循环在第二次循环完成相关初始化后，进入此分支。k = r & m & SQMASK = 0
            /** CASE3: 命中了任务队列，则将任务入队，并尝试创建/唤醒一个工作线程（Worker）；*/
            else if ((q = ws[k = r & m & SQMASK]) != null) {
                if (q.qlock == 0 && U.compareAndSwapInt(q, QLOCK, 0, 1)) {
                    ForkJoinTask<?>[] a = q.array;
                    int s = q.top; // s = 4096
                    boolean submitted = false; // initial submission or resizing //获取随机偶数槽位的workQueue
                    try {                      // locked version of push //锁定 workQueue
                        if ((a != null && a.length > s + 1 - q.base) || // a.length =8192
                            (a = q.growArray()) != null) { //扩容
                            int j = (((a.length - 1) & s) << ASHIFT) + ABASE;
                            U.putOrderedObject(a, j, task);//放入给定任务
                            U.putOrderedInt(q, QTOP, s + 1);//修改push slot
                            submitted = true;//会触发 signalWork(ws, q);方法
                        }
                    } finally {
                        U.compareAndSwapInt(q, QLOCK, 1, 0);//解除锁定
                    }
                    if (submitted) {//任务提交成功，创建或激活工作线程
                        /**
                         *任务执行的核心入口：如果hash之后的队列已经存在， lock住队列,将数据塞到top位置。如果该队列任务很少(n <= 1)也会调用signalWork
                         */
                        signalWork(ws, q);//创建或激活一个工作线程来运行任务
                        return;
                    }
                }
                move = true;                   // move on failure  操作失败，重新获取探针值
            }
            //第二次循环时：runState为start状态，并且没有，所以锁判断成立，进入此分支
            /**CASE4: 未命中任务队列，则在偶数索引处创建一个任务队列*/
            else if (((rs = runState) & RSLOCK) == 0) { // create new queue
                q = new WorkQueue(this, null);
                q.hint = r;
                q.config = k | SHARED_QUEUE; // k为任务队列在队列数组中的索引: k == r & m & SQMASK, 在CASE3的IF判断中赋值
                q.scanState = INACTIVE;
                rs = lockRunState();           // publish index
                if (rs > 0 &&  (ws = workQueues) != null &&
                    k < ws.length && ws[k] == null)
                    ws[k] = q;                 // else terminated  // 更新索引k位值的workQueue。第一次k=0
                unlockRunState(rs, rs & ~RSLOCK);
            }
            else
                move = true;                   // move if busy
            //第一次进入此分支，此时move = false
            if (move)
                r = ThreadLocalRandom.advanceProbe(r); //重新获取线程探针值
        }
    }

    /**
     * Tries to add the given task to a submission queue at
     * submitter's current queue. Only the (vastly) most common path
     * is directly handled in this method, while screening for need
     * for externalSubmit.
     * 尝试将给定的任务添加到提交者当前队列的"submission queue"中。这种方法只直接处理(非常)最常见的路径，同时判断是否需要externalSubmit。
     *
     * @param task the task. Caller must ensure non-null. 外部的任务实现(ForkJoinTask)
     *
     * 一、 从外部添加给定任务到submission队列中.注意：该方法是“随机提交”，随机提交指根据线程随机变量、任务队列数组信息，计算命中槽（即本次提交的任务应该添加到任务队列数组中的哪个队列），
     *              如果命中且队列中任务数<1，则创建或激活一个工作线程；否则，调用externalSubmit初始化队列，并入队。
     *
     * 二、 externalPush和externalSubmit两个方法的联系：
     *      1、它们的作用都是把任务放到队列中等待执行。不同的是，externalSubmit可以说是完整版的externalPush，
     *         在任务首次提交时，需要初始化workQueues及其他相关属性，这个初始化操作就是externalSubmit来完成的；
     *         而后再向池中提交的任务都是通过简化版的externalSubmit-externalPush来完成。
     *      2、externalPush的执行流程很简单：
     *             首先找到一个随机偶数槽位的 workQueue，然后把任务放入这个 workQueue 的任务数组中，并更新top位。
     *             如果队列的剩余任务数小于1，则尝试创建或激活一个工作线程来运行任务（防止在externalSubmit初始化时发生异常导致工作线程创建失败）。
     */
    final void externalPush(ForkJoinTask<?> task) {
        WorkQueue[] ws;
        WorkQueue q;

        int m;//在后续赋值，为队列长度：(ws.length - 1)

        // 取得一个随机探查数，可能为0也可能为其它数
        int r = ThreadLocalRandom.getProbe(); //探针值，用于计算WorkQueue槽位索引 :第一次进入 r=0

        // 获取当前ForkJoinPool的运行状态
        int rs = runState;//第一次进入rs = 0
        if ((ws = workQueues) != null //第一次：workQueues = nulll，不会进入此分支。但后续fork出来的任务时，workQueue不为空
                && (m = (ws.length - 1)) >= 0
                /**
                 * 核心代码： q = ws[m & r & SQMASK]
                 * 1、m值为队列长度：(ws.length - 1)，例如当前WorkQueue数组大小为16，那么m的值就为15
                 * 2、r是一个线程独立的随机数生成器，关于java.util.concurrent.ThreadLocalRandom类的功能和使用方式可参见其它资料
                 * 3、而SQMASK是一个常量，值为126 （0x7e = 0b01111110）
                 * 结论：实际上，任何数和126进行“与”运算，其结果只可能是0或者偶数，即0 、 2 、 4 、 6 、 8。
                 *      也就是说以上代码中从名为“ws”的WorkQueue数组中，取出的元素只可能是第0个或者第偶数个队列。
                 */
                && (q = ws[m & r & SQMASK]) != null //获取随机偶数槽位的workQueue
                && r != 0
                && rs > 0
                && U.compareAndSwapInt(q, QLOCK, 0, 1)) {//对q进行加锁
            /**
             * 如果hash之后的队列已经存在
             * 则lock住队列,将数据塞到top位置。如果该队列任务很少(n <= 1)也会调用signalWork
             */
            //锁定workQueue
            ForkJoinTask<?>[] a; int am, n, s;
            if ((a = q.array) != null
                    &&  (am = a.length - 1) > (n = (s = q.top) - q.base)) {
                int j = ((am & s) << ASHIFT) + ABASE;//计算任务索引位置
                // 以下三个原子操作首先是将task放入队列
                U.putOrderedObject(a, j, task);//任务入列
                // 然后将“q”这个submission queue的top标记+1
                U.putOrderedInt(q, QTOP, s + 1);//更新push slot
                // 最后解除这个submission queue的锁定状态
                U.putIntVolatile(q, QLOCK, 0);//解除锁定
                // 如果条件成立，说明这时处于active的工作线程可能还不够，所以调用signalWork方法
                if (n <= 1)
                    //signalWork是fork/join框架中重要的方法之一,用于创建或者激活工作线程。
                    signalWork(ws, q);//任务数小于1时尝试创建或激活一个工作线程
                return;
            }
            // 这里试图接除对这个submission queue的锁定状态
            // 为什么会有两次接触呢？因为在之前代码中给队列加锁后，
            // 可能队列的现有空间并不满足添加新的task的条件
            U.compareAndSwapInt(q, QLOCK, 1, 0); //解除锁定
        }
        //如果第一次提交(或者是hash之后的队列还未初始化)，走这个分支，,调用externalSubmit
        externalSubmit(task);
    }

    /**
     * Returns common pool queue for an external thread.
     */
    static WorkQueue commonSubmitterQueue() {
        ForkJoinPool p = common;
        int r = ThreadLocalRandom.getProbe();
        WorkQueue[] ws; int m;
        return (p != null && (ws = p.workQueues) != null &&
                (m = ws.length - 1) >= 0) ?
            ws[m & r & SQMASK] : null;
    }

    /**
     * Performs tryUnpush for an external submitter: Finds queue,
     * locks if apparently non-empty, validates upon locking, and
     * adjusts top. Each check can fail but rarely does.
     */
    final boolean tryExternalUnpush(ForkJoinTask<?> task) {
        WorkQueue[] ws; WorkQueue w; ForkJoinTask<?>[] a; int m, s;
        int r = ThreadLocalRandom.getProbe();
        if ((ws = workQueues) != null && (m = ws.length - 1) >= 0 &&
            (w = ws[m & r & SQMASK]) != null &&
            (a = w.array) != null && (s = w.top) != w.base) {
            long j = (((a.length - 1) & (s - 1)) << ASHIFT) + ABASE;
            if (U.compareAndSwapInt(w, QLOCK, 0, 1)) {
                if (w.top == s && w.array == a &&
                    U.getObject(a, j) == task &&
                    U.compareAndSwapObject(a, j, task, null)) {
                    U.putOrderedInt(w, QTOP, s - 1);
                    U.putOrderedInt(w, QLOCK, 0);
                    return true;
                }
                U.compareAndSwapInt(w, QLOCK, 1, 0);
            }
        }
        return false;
    }

    /**
     * Performs helpComplete for an external submitter.
     */
    final int externalHelpComplete(CountedCompleter<?> task, int maxTasks) {
        WorkQueue[] ws; int n;
        int r = ThreadLocalRandom.getProbe();
        return ((ws = workQueues) == null || (n = ws.length) == 0) ? 0 :
            helpComplete(ws[(n - 1) & r & SQMASK], task, maxTasks);
    }

    // Exported methods

    // Constructors

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}, using the {@linkplain
     * #defaultForkJoinWorkerThreadFactory default thread factory},
     * no UncaughtExceptionHandler, and non-async LIFO processing mode.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     *  ForkJoin的默认构造函数
     */
    public ForkJoinPool() {
        //通过Math.min取MAX_CAP和Runtime.getRuntime().availableProcessors()较小值。
        //一般情况值为后者，即CPU内核数
        this(Math.min(MAX_CAP, Runtime.getRuntime().availableProcessors()),
             defaultForkJoinWorkerThreadFactory, null, false);
    }

    /**
     * Creates a {@code ForkJoinPool} with the indicated parallelism
     * level, the {@linkplain
     * #defaultForkJoinWorkerThreadFactory default thread factory},
     * no UncaughtExceptionHandler, and non-async LIFO processing mode.
     *
     * @param parallelism the parallelism level
     * @throws IllegalArgumentException if parallelism less than or
     *         equal to zero, or greater than implementation limit
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool(int parallelism) {
        this(parallelism, defaultForkJoinWorkerThreadFactory, null, false);
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters.
     *
     * @param parallelism the parallelism level. For default value,
     * use {@link java.lang.Runtime#availableProcessors}.
     *                 并行度，默认为CPU数，最小为1
     * @param factory the factory for creating new threads. For default value,
     * use {@link #defaultForkJoinWorkerThreadFactory}.
     *                工作线程工厂；
     * @param handler the handler for internal worker threads that
     * terminate due to unrecoverable errors encountered while executing
     * tasks. For default value, use {@code null}.
     *                处理工作线程运行任务时的异常情况类，默认为null；
     * @param asyncMode if true,
     * establishes local first-in-first-out scheduling mode for forked
     * tasks that are never joined. This mode may be more appropriate
     * than default locally stack-based mode in applications in which
     * worker threads only process event-style asynchronous tasks.
     * For default value, use {@code false}.
     *                  是否为异步模式，默认为 false。如果为true，表示子任务的执行遵循 FIFO 顺序并且任务不能被合并（join）
     *                  ，这种模式适用于工作线程只运行事件类型的异步任务。在多数场景使用时，如果没有太强的业务需求，
     *                  我们一般直接使用 ForkJoinPool 中的common池，在JDK1.8之后提供了ForkJoinPool.commonPool()方法可以直接使用common池。
     * @throws IllegalArgumentException if parallelism less than or
     *         equal to zero, or greater than implementation limit
     * @throws NullPointerException if the factory is null
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool(int parallelism,        //工作线程最大值
                        ForkJoinWorkerThreadFactory factory,
                        UncaughtExceptionHandler handler,
                        boolean asyncMode //默认false
    ) {
        this(checkParallelism(parallelism),
             checkFactory(factory),
             handler,
             asyncMode ? FIFO_QUEUE : LIFO_QUEUE,
             "ForkJoinPool-" + nextPoolId() + "-worker-");
        checkPermission();
    }

    private static int checkParallelism(int parallelism) {
        if (parallelism <= 0 || parallelism > MAX_CAP)
            throw new IllegalArgumentException();
        return parallelism;
    }

    private static ForkJoinWorkerThreadFactory checkFactory
        (ForkJoinWorkerThreadFactory factory) {
        if (factory == null)
            throw new NullPointerException();
        return factory;
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters, without
     * any security checks or parameter validation.  Invoked directly by
     * makeCommonPool.
     *构造函数之一
     */
    private ForkJoinPool(int parallelism,
                         ForkJoinWorkerThreadFactory factory,
                         UncaughtExceptionHandler handler,
                         int mode,
                         String workerNamePrefix) {
        this.workerNamePrefix = workerNamePrefix;
        this.factory = factory;
        this.ueh = handler;
        // (parallelism & SMASK),仅保留SMASK所有的32位值，即parallelism值，去除其它位值干扰
        // mode是由FIFO_QUEUE : LIFO_QUEUE运算而来,即设置了第16位是否为1.
        this.config = (parallelism & SMASK) | mode;
        //ctl中的高16（即AC）用来控制活动线程，使用负数来控制，当AC < 0，表示还有空闲的活动线程数。
        //默认值为“并发度parallelism”，即内核数。
        long np = (long)(-parallelism); // offset ctl counts
        /**
         * 在4核CPU下，即parallelism = 4;
         * 第一步：(高置AC，高16位为00000000_000000100，转换为10进制为-4)
         *    0b10000000_000000100_00000000_00000000_00000000_00000000_00000000_00000000
         * 第二步：(设置TC，高33到48位，为00000000_000000100，转换为10进制为4)
         *    0b00000000_000000000_00000000_00000100_00000000_00000000_00000000_00000000
         * 第三步：或运算。
         *
         * 总之，通过位运行完成了AC，TC的值初始化
          */

        this.ctl = ((np << AC_SHIFT) & AC_MASK) | ((np << TC_SHIFT) & TC_MASK);
    }

    /**
     * Returns the common pool instance. This pool is statically
     * constructed; its run state is unaffected by attempts to {@link
     * #shutdown} or {@link #shutdownNow}. However this pool and any
     * ongoing processing are automatically terminated upon program
     * {@link System#exit}.  Any program that relies on asynchronous
     * task processing to complete before program termination should
     * invoke {@code commonPool().}{@link #awaitQuiescence awaitQuiescence},
     * before exit.
     *
     * @return the common pool instance
     * @since 1.8
     */
    public static ForkJoinPool commonPool() {
        // assert common != null : "static init error";
        return common;
    }

    // Execution methods

    /**
     * Performs the given task, returning its result upon completion.
     * If the computation encounters an unchecked Exception or Error,
     * it is rethrown as the outcome of this invocation.  Rethrown
     * exceptions behave in the same way as regular exceptions, but,
     * when possible, contain stack traces (as displayed for example
     * using {@code ex.printStackTrace()}) of both the current thread
     * as well as the thread actually encountering the exception;
     * minimally only the latter.
     * 同步的任务提交，阻塞直到任务完成并返回结果。可参考execute(ForkJoinTask<T> task)和invoke(ForkJoinTask<T> task)
     * @param task the task
     * @param <T> the type of the task's result
     * @return the task's result
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> T invoke(ForkJoinTask<T> task) {
        if (task == null)
            throw new NullPointerException();
        externalPush(task);
        return task.join();
    }

    /**
     * Arranges for (asynchronous) execution of the given task.
     * 异步的任务提交，没有返回值。可参考submit(ForkJoinTask<T> task)和invoke(ForkJoinTask<T> task)
     * @param task the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public void execute(ForkJoinTask<?> task) {
        if (task == null)
            throw new NullPointerException();
        externalPush(task);
    }

    // AbstractExecutorService methods

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public void execute(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = new ForkJoinTask.RunnableExecuteAction(task);
        externalPush(job);
    }

    /**
     * Submits a ForkJoinTask for execution.
     * 提交一个ForkJoinTask，然后执行
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @return the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     *         此方法由外部线程调用（非ForkJoinThread线程），所以任务将添加在workQueues的偶数位
     *         叫submission队列。
     */
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        if (task == null)
            throw new NullPointerException();
        //添加给定任务到submission队列中,里面会调用externalSubmit()方法
        externalPush(task);
        return task;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     *  ForkJoinPool提供的提交接口很多,不管提交的是Callable、Runnable、ForkJoinTask最终都会转换成ForkJoinTask类型的任务,
     *  调用方法externalPush(ForkJoinTask<?> task)来进行提交逻辑
     */
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        ForkJoinTask<T> job = new ForkJoinTask.AdaptedCallable<T>(task);
        externalPush(job);
        return job;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        ForkJoinTask<T> job = new ForkJoinTask.AdaptedRunnable<T>(task, result);
        externalPush(job);
        return job;
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public ForkJoinTask<?> submit(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = new ForkJoinTask.AdaptedRunnableAction(task);
        externalPush(job);
        return job;
    }

    /**
     * @throws NullPointerException       {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     */
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        // In previous versions of this class, this method constructed
        // a task to run ForkJoinTask.invokeAll, but now external
        // invocation of multiple tasks is at least as efficient.
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());

        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f = new ForkJoinTask.AdaptedCallable<T>(t);
                futures.add(f);
                externalPush(f);
            }
            for (int i = 0, size = futures.size(); i < size; i++)
                ((ForkJoinTask<?>)futures.get(i)).quietlyJoin();
            done = true;
            return futures;
        } finally {
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(false);
        }
    }

    /**
     * Returns the factory used for constructing new workers.
     *
     * @return the factory used for constructing new workers
     */
    public ForkJoinWorkerThreadFactory getFactory() {
        return factory;
    }

    /**
     * Returns the handler for internal worker threads that terminate
     * due to unrecoverable errors encountered while executing tasks.
     *
     * @return the handler, or {@code null} if none
     */
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return ueh;
    }

    /**
     * Returns the targeted parallelism level of this pool.
     *
     * @return the targeted parallelism level of this pool
     */
    public int getParallelism() {
        int par;
        return ((par = config & SMASK) > 0) ? par : 1;
    }

    /**
     * Returns the targeted parallelism level of the common pool.
     *
     * @return the targeted parallelism level of the common pool
     * @since 1.8
     */
    public static int getCommonPoolParallelism() {
        return commonParallelism;
    }

    /**
     * Returns the number of worker threads that have started but not
     * yet terminated.  The result returned by this method may differ
     * from {@link #getParallelism} when threads are created to
     * maintain parallelism when others are cooperatively blocked.
     *
     * @return the number of worker threads
     */
    public int getPoolSize() {
        return (config & SMASK) + (short)(ctl >>> TC_SHIFT);
    }

    /**
     * Returns {@code true} if this pool uses local first-in-first-out
     * scheduling mode for forked tasks that are never joined.
     *
     * @return {@code true} if this pool uses async mode
     */
    public boolean getAsyncMode() {
        return (config & FIFO_QUEUE) != 0;
    }

    /**
     * Returns an estimate of the number of worker threads that are
     * not blocked waiting to join tasks or for other managed
     * synchronization. This method may overestimate the
     * number of running threads.
     *
     * @return the number of worker threads
     */
    public int getRunningThreadCount() {
        int rc = 0;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null && w.isApparentlyUnblocked())
                    ++rc;
            }
        }
        return rc;
    }

    /**
     * Returns an estimate of the number of threads that are currently
     * stealing or executing tasks. This method may overestimate the
     * number of active threads.
     *
     * @return the number of active threads
     */
    public int getActiveThreadCount() {
        int r = (config & SMASK) + (int)(ctl >> AC_SHIFT);
        return (r <= 0) ? 0 : r; // suppress momentarily negative values
    }

    /**
     * Returns {@code true} if all worker threads are currently idle.
     * An idle worker is one that cannot obtain a task to execute
     * because none are available to steal from other threads, and
     * there are no pending submissions to the pool. This method is
     * conservative; it might not return {@code true} immediately upon
     * idleness of all threads, but will eventually become true if
     * threads remain inactive.
     *
     * @return {@code true} if all threads are currently idle
     */
    public boolean isQuiescent() {
        return (config & SMASK) + (int)(ctl >> AC_SHIFT) <= 0;
    }

    /**
     * Returns an estimate of the total number of tasks stolen from
     * one thread's work queue by another. The reported value
     * underestimates the actual total number of steals when the pool
     * is not quiescent. This value may be useful for monitoring and
     * tuning fork/join programs: in general, steal counts should be
     * high enough to keep threads busy, but low enough to avoid
     * overhead and contention across threads.
     *
     * @return the number of steals
     */
    public long getStealCount() {
        AtomicLong sc = stealCounter;
        long count = (sc == null) ? 0L : sc.get();
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += w.nsteals;
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the total number of tasks currently held
     * in queues by worker threads (but not including tasks submitted
     * to the pool that have not begun executing). This value is only
     * an approximation, obtained by iterating across all threads in
     * the pool. This method may be useful for tuning task
     * granularities.
     *
     * @return the number of queued tasks
     */
    public long getQueuedTaskCount() {
        long count = 0;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += w.queueSize();
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the number of tasks submitted to this
     * pool that have not yet begun executing.  This method may take
     * time proportional to the number of submissions.
     *
     * @return the number of queued submissions
     */
    public int getQueuedSubmissionCount() {
        int count = 0;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += w.queueSize();
            }
        }
        return count;
    }

    /**
     * Returns {@code true} if there are any tasks submitted to this
     * pool that have not yet begun executing.
     *
     * @return {@code true} if there are any queued submissions
     */
    public boolean hasQueuedSubmissions() {
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; i += 2) {
                if ((w = ws[i]) != null && !w.isEmpty())
                    return true;
            }
        }
        return false;
    }

    /**
     * Removes and returns the next unexecuted submission if one is
     * available.  This method may be useful in extensions to this
     * class that re-assign work in systems with multiple pools.
     *
     * @return the next submission, or {@code null} if none
     */
    protected ForkJoinTask<?> pollSubmission() {
        WorkQueue[] ws; WorkQueue w; ForkJoinTask<?> t;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; i += 2) {
                if ((w = ws[i]) != null && (t = w.poll()) != null)
                    return t;
            }
        }
        return null;
    }

    /**
     * Removes all available unexecuted submitted and forked tasks
     * from scheduling queues and adds them to the given collection,
     * without altering their execution status. These may include
     * artificially generated or wrapped tasks. This method is
     * designed to be invoked only when the pool is known to be
     * quiescent. Invocations at other times may not remove all
     * tasks. A failure encountered while attempting to add elements
     * to collection {@code c} may result in elements being in
     * neither, either or both collections when the associated
     * exception is thrown.  The behavior of this operation is
     * undefined if the specified collection is modified while the
     * operation is in progress.
     *
     * @param c the collection to transfer elements into
     * @return the number of elements transferred
     */
    protected int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
        int count = 0;
        WorkQueue[] ws; WorkQueue w; ForkJoinTask<?> t;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; ++i) {
                if ((w = ws[i]) != null) {
                    while ((t = w.poll()) != null) {
                        c.add(t);
                        ++count;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state, parallelism level, and
     * worker and task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        // Use a single pass through workQueues to collect counts
        long qt = 0L, qs = 0L; int rc = 0;
        AtomicLong sc = stealCounter;
        long st = (sc == null) ? 0L : sc.get();
        long c = ctl;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; ++i) {
                if ((w = ws[i]) != null) {
                    int size = w.queueSize();
                    if ((i & 1) == 0)
                        qs += size;
                    else {
                        qt += size;
                        st += w.nsteals;
                        if (w.isApparentlyUnblocked())
                            ++rc;
                    }
                }
            }
        }
        int pc = (config & SMASK);
        int tc = pc + (short)(c >>> TC_SHIFT);
        int ac = pc + (int)(c >> AC_SHIFT);
        if (ac < 0) // ignore transient negative
            ac = 0;
        int rs = runState;
        String level = ((rs & TERMINATED) != 0 ? "Terminated" :
                        (rs & STOP)       != 0 ? "Terminating" :
                        (rs & SHUTDOWN)   != 0 ? "Shutting down" :
                        "Running");
        return super.toString() +
            "[" + level +
            ", parallelism = " + pc +
            ", size = " + tc +
            ", active = " + ac +
            ", running = " + rc +
            ", steals = " + st +
            ", tasks = " + qt +
            ", submissions = " + qs +
            "]";
    }

    /**
     * Possibly initiates an orderly shutdown in which previously
     * submitted tasks are executed, but no new tasks will be
     * accepted. Invocation has no effect on execution state if this
     * is the {@link #commonPool()}, and no additional effect if
     * already shut down.  Tasks that are in the process of being
     * submitted concurrently during the course of this method may or
     * may not be rejected.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public void shutdown() {
        checkPermission();
        tryTerminate(false, true);
    }

    /**
     * Possibly attempts to cancel and/or stop all tasks, and reject
     * all subsequently submitted tasks.  Invocation has no effect on
     * execution state if this is the {@link #commonPool()}, and no
     * additional effect if already shut down. Otherwise, tasks that
     * are in the process of being submitted or executed concurrently
     * during the course of this method may or may not be
     * rejected. This method cancels both existing and unexecuted
     * tasks, in order to permit termination in the presence of task
     * dependencies. So the method always returns an empty list
     * (unlike the case for some other Executors).
     *
     * @return an empty list
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public List<Runnable> shutdownNow() {
        checkPermission();
        tryTerminate(true, true);
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return (runState & TERMINATED) != 0;
    }

    /**
     * Returns {@code true} if the process of termination has
     * commenced but not yet completed.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, or are waiting for I/O,
     * causing this executor not to properly terminate. (See the
     * advisory notes for class {@link ForkJoinTask} stating that
     * tasks should not normally entail blocking operations.  But if
     * they do, they must abort them on interrupt.)
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int rs = runState;
        return (rs & STOP) != 0 && (rs & TERMINATED) == 0;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return (runState & SHUTDOWN) != 0;
    }

    /**
     * Blocks until all tasks have completed execution after a
     * shutdown request, or the timeout occurs, or the current thread
     * is interrupted, whichever happens first. Because the {@link
     * #commonPool()} never terminates until program shutdown, when
     * applied to the common pool, this method is equivalent to {@link
     * #awaitQuiescence(long, TimeUnit)} but always returns {@code false}.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this executor terminated and
     *         {@code false} if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (this == common) {
            awaitQuiescence(timeout, unit);
            return false;
        }
        long nanos = unit.toNanos(timeout);
        if (isTerminated())
            return true;
        if (nanos <= 0L)
            return false;
        long deadline = System.nanoTime() + nanos;
        synchronized (this) {
            for (;;) {
                if (isTerminated())
                    return true;
                if (nanos <= 0L)
                    return false;
                long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
                wait(millis > 0L ? millis : 1L);
                nanos = deadline - System.nanoTime();
            }
        }
    }

    /**
     * If called by a ForkJoinTask operating in this pool, equivalent
     * in effect to {@link ForkJoinTask#helpQuiesce}. Otherwise,
     * waits and/or attempts to assist performing tasks until this
     * pool {@link #isQuiescent} or the indicated timeout elapses.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if quiescent; {@code false} if the
     * timeout elapsed.
     */
    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        long nanos = unit.toNanos(timeout);
        ForkJoinWorkerThread wt;
        Thread thread = Thread.currentThread();
        if ((thread instanceof ForkJoinWorkerThread) &&
            (wt = (ForkJoinWorkerThread)thread).pool == this) {
            helpQuiescePool(wt.workQueue);
            return true;
        }
        long startTime = System.nanoTime();
        WorkQueue[] ws;
        int r = 0, m;
        boolean found = true;
        while (!isQuiescent() && (ws = workQueues) != null &&
               (m = ws.length - 1) >= 0) {
            if (!found) {
                if ((System.nanoTime() - startTime) > nanos)
                    return false;
                Thread.yield(); // cannot block
            }
            found = false;
            for (int j = (m + 1) << 2; j >= 0; --j) {
                ForkJoinTask<?> t; WorkQueue q; int b, k;
                if ((k = r++ & m) <= m && k >= 0 && (q = ws[k]) != null &&
                    (b = q.base) - q.top < 0) {
                    found = true;
                    if ((t = q.pollAt(b)) != null)
                        t.doExec();
                    break;
                }
            }
        }
        return true;
    }

    /**
     * Waits and/or attempts to assist performing tasks indefinitely
     * until the {@link #commonPool()} {@link #isQuiescent}.
     */
    static void quiesceCommonPool() {
        common.awaitQuiescence(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    /**
     * Interface for extending managed parallelism for tasks running
     * in {@link ForkJoinPool}s.
     *
     * <p>A {@code ManagedBlocker} provides two methods.  Method
     * {@link #isReleasable} must return {@code true} if blocking is
     * not necessary. Method {@link #block} blocks the current thread
     * if necessary (perhaps internally invoking {@code isReleasable}
     * before actually blocking). These actions are performed by any
     * thread invoking {@link ForkJoinPool#managedBlock(ManagedBlocker)}.
     * The unusual methods in this API accommodate synchronizers that
     * may, but don't usually, block for long periods. Similarly, they
     * allow more efficient internal handling of cases in which
     * additional workers may be, but usually are not, needed to
     * ensure sufficient parallelism.  Toward this end,
     * implementations of method {@code isReleasable} must be amenable
     * to repeated invocation.
     *
     * <p>For example, here is a ManagedBlocker based on a
     * ReentrantLock:
     *  <pre> {@code
     * class ManagedLocker implements ManagedBlocker {
     *   final ReentrantLock lock;
     *   boolean hasLock = false;
     *   ManagedLocker(ReentrantLock lock) { this.lock = lock; }
     *   public boolean block() {
     *     if (!hasLock)
     *       lock.lock();
     *     return true;
     *   }
     *   public boolean isReleasable() {
     *     return hasLock || (hasLock = lock.tryLock());
     *   }
     * }}</pre>
     *
     * <p>Here is a class that possibly blocks waiting for an
     * item on a given queue:
     *  <pre> {@code
     * class QueueTaker<E> implements ManagedBlocker {
     *   final BlockingQueue<E> queue;
     *   volatile E item = null;
     *   QueueTaker(BlockingQueue<E> q) { this.queue = q; }
     *   public boolean block() throws InterruptedException {
     *     if (item == null)
     *       item = queue.take();
     *     return true;
     *   }
     *   public boolean isReleasable() {
     *     return item != null || (item = queue.poll()) != null;
     *   }
     *   public E getItem() { // call after pool.managedBlock completes
     *     return item;
     *   }
     * }}</pre>
     */
    public static interface ManagedBlocker {
        /**
         * Possibly blocks the current thread, for example waiting for
         * a lock or condition.
         *
         * @return {@code true} if no additional blocking is necessary
         * (i.e., if isReleasable would return true)
         * @throws InterruptedException if interrupted while waiting
         * (the method is not required to do so, but is allowed to)
         */
        boolean block() throws InterruptedException;

        /**
         * Returns {@code true} if blocking is unnecessary.
         * @return {@code true} if blocking is unnecessary
         */
        boolean isReleasable();
    }

    /**
     * Runs the given possibly blocking task.  When {@linkplain
     * ForkJoinTask#inForkJoinPool() running in a ForkJoinPool}, this
     * method possibly arranges for a spare thread to be activated if
     * necessary to ensure sufficient parallelism while the current
     * thread is blocked in {@link ManagedBlocker#block blocker.block()}.
     *
     * <p>This method repeatedly calls {@code blocker.isReleasable()} and
     * {@code blocker.block()} until either method returns {@code true}.
     * Every call to {@code blocker.block()} is preceded by a call to
     * {@code blocker.isReleasable()} that returned {@code false}.
     *
     * <p>If not running in a ForkJoinPool, this method is
     * behaviorally equivalent to
     *  <pre> {@code
     * while (!blocker.isReleasable())
     *   if (blocker.block())
     *     break;}</pre>
     *
     * If running in a ForkJoinPool, the pool may first be expanded to
     * ensure sufficient parallelism available during the call to
     * {@code blocker.block()}.
     *
     * @param blocker the blocker task
     * @throws InterruptedException if {@code blocker.block()} did so
     * 为 ForkJoinPool 中的任务提供扩展管理并行数的接口，一般用在可能会阻塞的任务（如在 Phaser 中用于等待 phase 到下一个generation）
     */
    public static void managedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        ForkJoinPool p;
        ForkJoinWorkerThread wt;
        Thread t = Thread.currentThread();
        if ((t instanceof ForkJoinWorkerThread) &&
            (p = (wt = (ForkJoinWorkerThread)t).pool) != null) {
            WorkQueue w = wt.workQueue;
            while (!blocker.isReleasable()) {
                if (p.tryCompensate(w)) {
                    try {
                        do {} while (!blocker.isReleasable() &&
                                     !blocker.block());
                    } finally {
                        U.getAndAddLong(p, CTL, AC_UNIT);
                    }
                    break;
                }
            }
        }
        else {
            do {} while (!blocker.isReleasable() &&
                         !blocker.block());
        }
    }

    // AbstractExecutorService overrides.  These rely on undocumented
    // fact that ForkJoinTask.adapt returns ForkJoinTasks that also
    // implement RunnableFuture.

    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new ForkJoinTask.AdaptedRunnable<T>(runnable, value);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new ForkJoinTask.AdaptedCallable<T>(callable);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final int  ABASE;
    private static final int  ASHIFT;
    private static final long CTL;
    private static final long RUNSTATE;
    private static final long STEALCOUNTER;
    private static final long PARKBLOCKER;
    private static final long QTOP;
    private static final long QLOCK;
    private static final long QSCANSTATE;
    private static final long QPARKER;
    private static final long QCURRENTSTEAL;
    private static final long QCURRENTJOIN;

    static {
        // initialize field offsets for CAS etc
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> k = ForkJoinPool.class;
            CTL = U.objectFieldOffset
                (k.getDeclaredField("ctl"));
            RUNSTATE = U.objectFieldOffset
                (k.getDeclaredField("runState"));
            STEALCOUNTER = U.objectFieldOffset
                (k.getDeclaredField("stealCounter"));
            Class<?> tk = Thread.class;
            PARKBLOCKER = U.objectFieldOffset
                (tk.getDeclaredField("parkBlocker"));
            Class<?> wk = WorkQueue.class;
            QTOP = U.objectFieldOffset
                (wk.getDeclaredField("top"));
            QLOCK = U.objectFieldOffset
                (wk.getDeclaredField("qlock"));
            QSCANSTATE = U.objectFieldOffset
                (wk.getDeclaredField("scanState"));
            QPARKER = U.objectFieldOffset
                (wk.getDeclaredField("parker"));
            QCURRENTSTEAL = U.objectFieldOffset
                (wk.getDeclaredField("currentSteal"));
            QCURRENTJOIN = U.objectFieldOffset
                (wk.getDeclaredField("currentJoin"));
            Class<?> ak = ForkJoinTask[].class;
            ABASE = U.arrayBaseOffset(ak);
            int scale = U.arrayIndexScale(ak);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            throw new Error(e);
        }

        commonMaxSpares = DEFAULT_COMMON_MAX_SPARES;
        defaultForkJoinWorkerThreadFactory =
            new DefaultForkJoinWorkerThreadFactory();
        modifyThreadPermission = new RuntimePermission("modifyThread");

        common = java.security.AccessController.doPrivileged
            (new java.security.PrivilegedAction<ForkJoinPool>() {
                public ForkJoinPool run() { return makeCommonPool(); }});
        int par = common.config & SMASK; // report 1 even if threads disabled
        commonParallelism = par > 0 ? par : 1;
    }

    /**
     * Creates and returns the common pool, respecting user settings
     * specified via system properties.
     * 使用common pool的优点就是我们可以通过指定系统参数的方式定义“并行度、线程工厂和异常处理类”；
     * 并且它使用的是同步模式，也就是说可以支持任务合并（join）。
     */
    private static ForkJoinPool makeCommonPool() {
        int parallelism = -1;//并行度初始值
        ForkJoinWorkerThreadFactory factory = null;
        UncaughtExceptionHandler handler = null;
        // 可以通过在java程序启动时，指定这些参数的方式
        // 来完成并行等级，线程工厂，异常处理类的指定工作
        try {  // ignore exceptions in accessing/parsing properties
            //从系统中获取相应参数（如果有配置的话）
            String pp = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.parallelism"); //并行度
            String fp = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.threadFactory");//线程工厂
            String hp = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.exceptionHandler");//异常处理类
            if (pp != null)//有配置，则使用配置值
                parallelism = Integer.parseInt(pp);
            if (fp != null)//有配置则使用配置值，否则使用默认工厂
                factory = ((ForkJoinWorkerThreadFactory)ClassLoader.
                           getSystemClassLoader().loadClass(fp).newInstance());
            if (hp != null)
                handler = ((UncaughtExceptionHandler)ClassLoader.
                           getSystemClassLoader().loadClass(hp).newInstance());
        } catch (Exception ignore) {
        }
        if (factory == null) {//为空表示启动参数未配置，使用默认值defaultForkJoinWorkerThreadFactory
            // 如果当前没有启动SecurityManager，安全策略管理器
            // 这时使用defaultForkJoinWorkerThreadFactory这个工厂对象
            // 它是java.util.concurrent.ForkJoinPool.DefaultForkJoinWorkerThreadFactory这个类的实例
            /**
             * 对commonPool的初始化过程有Java security安全策略框架参与，doPrivileged方法为排除Java security安全策略框架的权限检查，
             * 而SecurityManager是Java security安全策略框架的管理器。一般情况下Java应用程序不会自动启动安全管理器，
             * 不过读者可以在Java应用程序启动时，使用-Djava.security.manager参数启动SecurityManager，
             * 或者在你的代码中通过System.setSecurityManager()方法显式设定一个。
             */
            if (System.getSecurityManager() == null)
                factory = defaultForkJoinWorkerThreadFactory;
            else // use security-managed default
                factory = new InnocuousForkJoinWorkerThreadFactory();
        }
        // 如果并行等级小于0，并且当前应用程序可用CPU内核数为1
        // 那么设定parallelism并行等级为1
        if (parallelism < 0 && // default 1 less than #cores
            (parallelism = Runtime.getRuntime().availableProcessors() - 1) <= 0)
            parallelism = 1;//默认并行度为1
        if (parallelism > MAX_CAP)
            parallelism = MAX_CAP;

        // 最后使用这个构造函数初始化commonPool
        return new ForkJoinPool(parallelism, factory, handler, LIFO_QUEUE,
                                "ForkJoinPool.commonPool-worker-");
    }

    /**
     * Factory for innocuous worker threads
     * 无许可线程工厂，当系统变量中有系统安全管理相关属性时，默认使用这个工厂创建工作线程。
     */
    static final class InnocuousForkJoinWorkerThreadFactory
        implements ForkJoinWorkerThreadFactory {

        /**
         * An ACC to restrict permissions for the factory itself.
         * The constructed workers have no permissions set.
         */
        private static final AccessControlContext innocuousAcc;
        static {
            Permissions innocuousPerms = new Permissions();
            innocuousPerms.add(modifyThreadPermission);
            innocuousPerms.add(new RuntimePermission(
                                   "enableContextClassLoaderOverride"));
            innocuousPerms.add(new RuntimePermission(
                                   "modifyThreadGroup"));
            innocuousAcc = new AccessControlContext(new ProtectionDomain[] {
                    new ProtectionDomain(null, innocuousPerms)
                });
        }

        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return (ForkJoinWorkerThread.InnocuousForkJoinWorkerThread)
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<ForkJoinWorkerThread>() {
                    public ForkJoinWorkerThread run() {
                        return new ForkJoinWorkerThread.
                            InnocuousForkJoinWorkerThread(pool);
                    }}, innocuousAcc);
        }
    }

}
