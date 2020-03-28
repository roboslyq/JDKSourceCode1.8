/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.channels;

import java.io.IOException;
import java.io.Closeable;


/**
 * A nexus for I/O operations.
 *
 * <p> A channel represents an open connection to an entity such as a hardware
 * device, a file, a network socket, or a program component that is capable(能力、胜任，能干) of
 * performing one or more distinct I/O operations, for example reading or
 * writing.
 * 一个Channel与一个打开了的链接对应,比如硬件设备,文件,网络或者其它有能力处理一个或者多个I/O相关操作(Read/Write)的组件.
 * <p> A channel is either open or closed.  A channel is open upon creation,
 * and once closed it remains closed.  Once a channel is closed, any attempt to
 * invoke an I/O operation upon it will cause a {@link ClosedChannelException}
 * to be thrown.  Whether or not a channel is open may be tested by invoking
 * its {@link #isOpen isOpen} method.
 *  Channel只有两种状态：open 和closed. 创建时为Open,关闭后就一直保持closed。并且一旦colsed就不能进行任何相关操作。
 *  否周日会抛出ClosedChannelException异常。我们可以通过isOpen()方法来判断CHannel的状态。
 * <p> Channels are, in general, intended to be safe for multithreaded access
 * as described in the specifications of the interfaces and classes that extend
 * and implement this interface.
 *  Channel应该是线程安全的，所有继承这个接口或者实现这个接口的类应该遵守这个约定。
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public interface Channel extends Closeable {

    /**
     * Tells whether or not this channel is open.
     *
     * @return <tt>true</tt> if, and only if, this channel is open
     */
    public boolean isOpen();

    /**
     * Closes this channel.
     *
     * <p> After a channel is closed, any further attempt to invoke I/O
     * operations upon it will cause a {@link ClosedChannelException} to be
     * thrown.
     *
     * <p> If this channel is already closed then invoking this method has no
     * effect.
     *
     * <p> This method may be invoked at any time.  If some other thread has
     * already invoked it, however, then another invocation will block until
     * the first invocation is complete, after which it will return without
     * effect. </p>
     *
     * @throws  IOException  If an I/O error occurs
     */
    public void close() throws IOException;

}
