package roboslyq.threadlocal;

public class ThreadLocalService1 {

    public void add(int add){
        Source source = SourcesManager.getSrc1();
        System.out.println("service1之前的age-->"+Thread.currentThread().getId()+"--->"+source.getAge());
        source.setAge(source.getAge()+add);
        System.out.println("service1之后的age-->"+Thread.currentThread().getId()+"--->"+source.getAge());
    }
}
