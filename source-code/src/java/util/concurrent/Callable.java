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

/**
 * A task that returns a result and may throw an exception.
 * Implementors define a single method with no arguments called
 * {@code call}.
 *
 * <p>The {@code Callable} interface is similar to {@link
 * java.lang.Runnable}, in that both are designed for classes whose
 * instances are potentially executed by another thread.  A
 * {@code Runnable}, however, does not return a result and cannot
 * throw a checked exception.
 *
 * <p>The {@link Executors} class contains utility methods to
 * convert from other common forms to {@code Callable} classes.
 *
 * @see Executor
 * @since 1.5
 * @author Doug Lea
 * @param <V> the result type of method {@code call}
 */
/**
 *
 * 1、当将一个Callable的对象或Runnable对象
 *   ,传递给ExecutorService的submit方法，则该对象的call()或run()会自动在一个线程上执行, 并且会返回执行结果Future对象
 *   Callable和Runnable有以下几点不同：<br>
 *   (1)、Callable规定的方法是call()，而Runnable规定的方法是run().<br>
 *   (2)、Callable的任务执行后可返回值，而Runnable的任务是不能返回值的.<br>
 *   (3)、call()方法可抛出异常，而run()方法是不能抛出异常的.<br>
 *  (4)、运行Callable任务可拿到一个Future对象,获取线程的执行结果.<br>
 *
 * 2、Callable类似于Runnable接口,实现Callable接口的类和实现Runnable的类都是可被其它线程执行的任务。
 *
 * 3、Callable执行原理是包装为FutureTask，此接口同时继承了Runnable接口，所以在run()方法中代理Callable中的call()方法。
 * 并且将Callable的输出赋值到属性变量“private Object outcome”中。然后通过Future.get()获取outcome变量值。
 *
 * 4、综上,callable底层其实就是通过Runable实现任务的执行，与Runable无异。
 */
@FunctionalInterface
public interface Callable<V> {
    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Exception if unable to compute a result
     */
    V call() throws Exception;
}
