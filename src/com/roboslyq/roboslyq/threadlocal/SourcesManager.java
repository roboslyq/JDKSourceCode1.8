package com.roboslyq.roboslyq.threadlocal;

import java.util.Random;

public class SourcesManager {

    public static ThreadLocal<Source> tl1 = new ThreadLocal<>();

    public static ThreadLocal<Source> tl2 = new ThreadLocal<>();

    public static Source getSrc1(){
        Source source = tl1.get();
        if(null == source){
            System.out.println("tl1.source为空，需要初始化");
            Source sourceTmp = new Source();
            sourceTmp.setAge((new Random()).nextInt(30));
            sourceTmp.setSrcId((new Random()).nextInt(100)+"");
            tl1.set(sourceTmp);
            System.out.println("tl1.source为空初始化值为--->"+sourceTmp.getAge());
            return tl1.get();
        }else{
            System.out.println("tl1.source已经存在，直接返回");
            return source;
        }
    }
    public static Source getSrc2(){
        return tl2.get();
    }
    public void setSrc1(Source source){
        tl1.set(source);
    }
    public void setSrc2(Source source){
        tl2.set(source);
    }

}
