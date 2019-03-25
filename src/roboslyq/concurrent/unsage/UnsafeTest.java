package roboslyq.concurrent.unsage;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class UnsafeTest {
    public static void main(String[] args) {
        try {
            Unsafe unsafe = UnsafeTest.getUnsafe();

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static Unsafe getUnsafe() throws NoSuchFieldException, IllegalAccessException {
       Unsafe unsafe = null;
       Field field = Unsafe.class.getDeclaredField("theUnsafe");
       field.setAccessible(true);
        unsafe = (Unsafe)field.get(null);
        return unsafe;
    }

    public static Unsafe getUnsafeByConstructor() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
        unsafeConstructor.setAccessible(true);
        Unsafe unsafe = unsafeConstructor.newInstance();
        return  unsafe;
    }
}
