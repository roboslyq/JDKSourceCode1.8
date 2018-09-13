package com.roboslyq.roboslyq.threadlocal;

public class ThreadLocalService2 {
    public void add(int add){
        Source source =   SourcesManager.getSrc1();
        System.out.println("service2之前的age-->"+Thread.currentThread().getId()+"--->"+source.getAge());
        source.setAge(source.getAge()+add);
        System.out.println("service2之后的age-->"+Thread.currentThread().getId()+"--->"+source.getAge());
    }
}
