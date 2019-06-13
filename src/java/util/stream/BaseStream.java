/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Base interface for streams, which are sequences of elements supporting
 * sequential and parallel aggregate operations.  The following example
 * illustrates an aggregate operation using the stream types {@link Stream}
 * and {@link IntStream}, computing the sum of the weights of the red widgets:
 *  BaseStream接口是Stream的基接口，支持串行和并行的聚合操作。
 * 该接口任然是AutoCloseable的子接口，支持了自动关闭流的功能。
 * 主要是定义以下相关接口：
 *      1、获取并行流或串行流，或者无序流
 *      2、判断流是否是并行和串行
 *      3、关装流及Close Handler
 *      4、获取流的遍列器
 * <pre>{@code
 *     int sum = widgets.stream()
 *                      .filter(w -> w.getColor() == RED)
 *                      .mapToInt(w -> w.getWeight())
 *                      .sum();
 * }</pre>
 *
 * See the class documentation for {@link Stream} and the package documentation
 * for <a href="package-summary.html">java.util.stream</a> for additional
 * specification of streams, stream operations, stream pipelines, and
 * parallelism, which governs the behavior of all stream types.
 *
 * @param <T> the type of the stream elements
 * @param <S> the type of of the stream implementing {@code BaseStream}
 * @since 1.8
 * @see Stream
 * @see IntStream
 * @see LongStream
 * @see DoubleStream
 * @see <a href="package-summary.html">java.util.stream</a>
 *
 * 泛型含义：
 *     T 是流中元素的类型
 *     S 是BaseStream的实现
 * 以Stream流为例，public interface Stream<T> extends BaseStream<T, Stream<T>>
 * Stream是BaseStream的子接口，其泛型是T和Stream<T>
 */
public interface BaseStream<T, S extends BaseStream<T, S>>
        extends AutoCloseable {
    /**
     * Returns an iterator for the elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return the element iterator for this stream
     *  该方法返回流中元素的迭代器，泛型就是T（流中元素的类型）
     */
    Iterator<T> iterator();

    /**
     * Returns a spliterator for the elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return the element spliterator for this stream
     * 该方法返回流中元素的分割迭代器，泛型就是T（流中元素的类型）
     */
    Spliterator<T> spliterator();

    /**
     * Returns whether this stream, if a terminal operation were to be executed,
     * would execute in parallel.  Calling this method after invoking an
     * terminal stream operation method may yield unpredictable results.
     *
     * @return {@code true} if this stream would execute in parallel if executed
     * 流的基础属性：是否并行流。即终止操作时，是否需要并行模式。
     */
    boolean isParallel();

    /**
     * Returns an equivalent stream that is sequential.  May return
     * itself, either because the stream was already sequential, or because
     * the underlying stream state was modified to be sequential.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a sequential stream
     * 该方法返回的是S，上述解释S就是一个新的Stream，新Stream的元素仍然是T，如果已经是串行的流调用此方法则返回本身。
     */
    S sequential();

    /**
     * Returns an equivalent stream that is parallel.  May return
     * itself, either because the stream was already parallel, or because
     * the underlying stream state was modified to be parallel.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a parallel stream
     * 该方法返回的是S，上述解释S就是一个新的Stream，新Stream的元素仍然是T，如果已经是并行的流调用此方法则返回本身。
     */
    S parallel();

    /**
     * Returns an equivalent stream that is
     * <a href="package-summary.html#Ordering">unordered</a>.  May return
     * itself, either because the stream was already unordered, or because
     * the underlying stream state was modified to be unordered.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return an unordered stream
     * 该方法返回的是S，上述解释S就是一个新的Stream，新Stream的元素仍然是T。
     * 新流是无序的，如果旧流本身是无序的或者已经设置为无序，则新流就是旧流本身。
     */
    S unordered();

    /**
     * Returns an equivalent stream with an additional close handler.  Close
     * handlers are run when the {@link #close()} method
     * is called on the stream, and are executed in the order they were
     * added.  All close handlers are run, even if earlier close handlers throw
     * exceptions.  If any close handler throws an exception, the first
     * exception thrown will be relayed to the caller of {@code close()}, with
     * any remaining exceptions added to that exception as suppressed exceptions
     * (unless one of the remaining exceptions is the same exception as the
     * first exception, since an exception cannot suppress itself.)  May
     * return itself.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param closeHandler A task to execute when the stream is closed
     * @return a stream with a handler that is run if the stream is closed
     * 该方法返回的是S，上述解释S就是一个新的Stream，新Stream的元素仍然是T，但此时的Stream已经添加了相应的CloseHandler。
     * 当close方法调用时，参数中的closeHandler才会执行，并且执行的顺序是添加的顺序。
     * 及时先添加的closeHandler抛出了异常，后续的closeHandler任会执行。
     * 第一个异常会传递到调用者处，后续的异常则是一起汇聚为suppressed exceptions。
     */
    S onClose(Runnable closeHandler);

    /**
     * Closes this stream, causing all close handlers for this stream pipeline
     * to be called.
     *
     * @see AutoCloseable#close()
     * 关闭流，会导致方法onClose(Runnable closeHandler)中所有的closeHandler被调用。
     */
    @Override
    void close();
}
