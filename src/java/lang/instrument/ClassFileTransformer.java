/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.instrument;

import  java.security.ProtectionDomain;

/*
 * Copyright 2003 Wily Technology, Inc.
 */

/**
 * An agent provides an implementation of this interface in order
 * to transform class files.
 * The transformation occurs before the class is defined by the JVM.
 * <P>
 * Note the term <i>class file</i> is used as defined in section 3.1 of
 * <cite>The Java&trade; Virtual Machine Specification</cite>,
 * to mean a sequence
 * of bytes in class file format, whether or not they reside in a file.
 *
 * 一个代理实现ClassFileTransformer接口用于改变运行时的字节码（class File），这个改变发生在jvm加载这个类之前。
 * 对所有的类加载器有效。
 * class File这个术语定义于虚拟机规范3.1，指的是字节码的byte数组，而不是文件系统中的class文件。
 *
 * 此接口中只有一个方法：
 * byte[] transform(  ClassLoader      loader,
 *                 String              className,
 *                 Class<?>            classBeingRedefined,
 *                 ProtectionDomain    protectionDomain,
 *                 byte[]              classfileBuffer)
 *         throws IllegalClassFormatException;
 * ClassFileTransformer需要添加到Instrumentation实例中才能生效。
 * 获取Instrumentation实例的方法有2种：
     * 虚拟机启动时，通过agent class的premain方法获得
     * 虚拟机启动后，通过agent class的agentmain方法获得
 * 一旦agent参数获取到一个instrumentation，agent将会在任意时候调用实例中的方法。
 *
 * agent应该以jar包的形式存在，也就是说agent所在的类需要单独打包一个jar包，jar包的manifest文件指定agent class。文件中包含Premain-Class属性，agent class类必须实现public static premain 方法，实际应用的main方法在这个方法之后执行。
 * premain 方法有2种签名，虚拟机优先调用
 *
 * public static void premain(String agentArgs, Instrumentation inst);
 * 1
 * 如果没有上一种，则调用下一种
 *
 * public static void premain(String agentArgs);
 * 1
 * 通过这个 -javaagent:jarpath[=options] 参数，启动实际应用，就会自带agent。如果agent启动失败，jvm会终止。
 *
 * 在虚拟机启动后，启动agent需要满足以下条件
 *
 * agent所在 的jar包的manifest文件中必须包含Agent-Class属性，值为agent class。
 * agent类必须有public static agentmain方法。
 * 系统类加载器必须支持添加一个agent的jar包到系统类路径system class path
 * 这个方法也有2种签名，优先加载第一种，第一种没有，就加载第二种。
 *
 * public static void agentmain(String agentArgs, Instrumentation inst);
 *
 * public static void agentmain(String agentArgs);
 * 1
 * 2
 * 3
 * 如果agent是在jvm启动后启动，那么premain就不会执行了。也就是说一个agent的2种方法只会启动一种。premain和agentmain是二选一的。
 * agentmain抛出异常，不会导致jvm终止。
 *
 * 第二种启动方式，先用jps获取进程id，然后启动agentjar包。
 * VirtualMachine 在jdk的lib下面的tools.jar中，如果不在classpath的话，要加进去。
 *
 * VirtualMachine vm = VirtualMachine.attach("3134");
 *  try {
 *  	vm.loadAgent("/../agent.jar");
 *  } finally
 *  {
 *  	vm.detach();
 * }
 * @see     java.lang.instrument.Instrumentation
 * @see     java.lang.instrument.Instrumentation#addTransformer
 * @see     java.lang.instrument.Instrumentation#removeTransformer
 * @since   1.5
 */

public interface ClassFileTransformer {
    /**
     * The implementation of this method may transform the supplied class file and
     * return a new replacement class file.
     *
     * <P>
     * There are two kinds of transformers, determined by the <code>canRetransform</code>
     * parameter of
     * {@link java.lang.instrument.Instrumentation#addTransformer(ClassFileTransformer,boolean)}:
     *  <ul>
     *    <li><i>retransformation capable</i> transformers that were added with
     *        <code>canRetransform</code> as true
     *    </li>
     *    <li><i>retransformation incapable</i> transformers that were added with
     *        <code>canRetransform</code> as false or where added with
     *        {@link java.lang.instrument.Instrumentation#addTransformer(ClassFileTransformer)}
     *    </li>
     *  </ul>
     *
     * <P>
     * Once a transformer has been registered with
     * {@link java.lang.instrument.Instrumentation#addTransformer(ClassFileTransformer,boolean)
     * addTransformer},
     * the transformer will be called for every new class definition and every class redefinition.
     * Retransformation capable transformers will also be called on every class retransformation.
     * The request for a new class definition is made with
     * {@link java.lang.ClassLoader#defineClass ClassLoader.defineClass}
     * or its native equivalents.
     * The request for a class redefinition is made with
     * {@link java.lang.instrument.Instrumentation#redefineClasses Instrumentation.redefineClasses}
     * or its native equivalents.
     * The request for a class retransformation is made with
     * {@link java.lang.instrument.Instrumentation#retransformClasses Instrumentation.retransformClasses}
     * or its native equivalents.
     * The transformer is called during the processing of the request, before the class file bytes
     * have been verified or applied.
     * When there are multiple transformers, transformations are composed by chaining the
     * <code>transform</code> calls.
     * That is, the byte array returned by one call to <code>transform</code> becomes the input
     * (via the <code>classfileBuffer</code> parameter) to the next call.
     *
     * <P>
     * Transformations are applied in the following order:
     *  <ul>
     *    <li>Retransformation incapable transformers
     *    </li>
     *    <li>Retransformation incapable native transformers
     *    </li>
     *    <li>Retransformation capable transformers
     *    </li>
     *    <li>Retransformation capable native transformers
     *    </li>
     *  </ul>
     *
     * <P>
     * For retransformations, the retransformation incapable transformers are not
     * called, instead the result of the previous transformation is reused.
     * In all other cases, this method is called.
     * Within each of these groupings, transformers are called in the order registered.
     * Native transformers are provided by the <code>ClassFileLoadHook</code> event
     * in the Java Virtual Machine Tool Interface).
     *
     * <P>
     * The input (via the <code>classfileBuffer</code> parameter) to the first
     * transformer is:
     *  <ul>
     *    <li>for new class definition,
     *        the bytes passed to <code>ClassLoader.defineClass</code>
     *    </li>
     *    <li>for class redefinition,
     *        <code>definitions.getDefinitionClassFile()</code> where
     *        <code>definitions</code> is the parameter to
     *        {@link java.lang.instrument.Instrumentation#redefineClasses
     *         Instrumentation.redefineClasses}
     *    </li>
     *    <li>for class retransformation,
     *         the bytes passed to the new class definition or, if redefined,
     *         the last redefinition, with all transformations made by retransformation
     *         incapable transformers reapplied automatically and unaltered;
     *         for details see
     *         {@link java.lang.instrument.Instrumentation#retransformClasses
     *          Instrumentation.retransformClasses}
     *    </li>
     *  </ul>
     *
     * <P>
     * If the implementing method determines that no transformations are needed,
     * it should return <code>null</code>.
     * Otherwise, it should create a new <code>byte[]</code> array,
     * copy the input <code>classfileBuffer</code> into it,
     * along with all desired transformations, and return the new array.
     * The input <code>classfileBuffer</code> must not be modified.
     *
     * <P>
     * In the retransform and redefine cases,
     * the transformer must support the redefinition semantics:
     * if a class that the transformer changed during initial definition is later
     * retransformed or redefined, the
     * transformer must insure that the second class output class file is a legal
     * redefinition of the first output class file.
     *
     * <P>
     * If the transformer throws an exception (which it doesn't catch),
     * subsequent transformers will still be called and the load, redefine
     * or retransform will still be attempted.
     * Thus, throwing an exception has the same effect as returning <code>null</code>.
     * To prevent unexpected behavior when unchecked exceptions are generated
     * in transformer code, a transformer can catch <code>Throwable</code>.
     * If the transformer believes the <code>classFileBuffer</code> does not
     * represent a validly formatted class file, it should throw
     * an <code>IllegalClassFormatException</code>;
     * while this has the same effect as returning null. it facilitates the
     * logging or debugging of format corruptions.
     *
     * @param loader                the defining loader of the class to be transformed,
     *                              may be <code>null</code> if the bootstrap loader
     * @param className             the name of the class in the internal form of fully
     *                              qualified class and interface names as defined in
     *                              <i>The Java Virtual Machine Specification</i>.
     *                              For example, <code>"java/util/List"</code>.
     * @param classBeingRedefined   if this is triggered by a redefine or retransform,
     *                              the class being redefined or retransformed;
     *                              if this is a class load, <code>null</code>
     * @param protectionDomain      the protection domain of the class being defined or redefined
     * @param classfileBuffer       the input byte buffer in class file format - must not be modified
     *
     * @throws IllegalClassFormatException if the input does not represent a well-formed class file
     * @return  a well-formed class file buffer (the result of the transform),
                or <code>null</code> if no transform is performed.
     * @see Instrumentation#redefineClasses
     */
    byte[]
    transform(  ClassLoader         loader,
                String              className,
                Class<?>            classBeingRedefined,
                ProtectionDomain    protectionDomain,
                byte[]              classfileBuffer)
        throws IllegalClassFormatException;
}
