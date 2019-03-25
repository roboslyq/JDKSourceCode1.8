package roboslyq;

public class Test {
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;


    // runState is stored in the high-order bits
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    public static void main(String[] args) {
        System.out.println(COUNT_BITS);
        System.out.println(CAPACITY);
        System.out.println(RUNNING);
        System.out.println(SHUTDOWN);
        System.out.println(STOP);
        System.out.println(TIDYING);
        System.out.println(TERMINATED);

        Test test = new Test();
        try {
            int result = test.fibonacci(10);
            System.out.println("fibonacci = "+result);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public int fibonacci(int i) throws Exception {
        if(i<= 0){
            throw  new Exception();
        }
        if(i == 0){
            return 0;
        }else if(i== 1){
            return 1;
        }else{
            return fibonacci(i-1)+fibonacci(i-2);
        }
    }

}
class User{
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}