package com.roboslyq.roboslyq.threadlocal;

public class ThreadLocalService3 {
    public void add(int add){
        Source source =   SourcesManager.getSrc1();
        System.out.println("service3之前的age-->"+Thread.currentThread().getId()+"--->"+source.getAge());
        source.setAge(source.getAge()+add);
        System.out.println("service3之后的age-->"+Thread.currentThread().getId()+"--->"+source.getAge());
    }
}
