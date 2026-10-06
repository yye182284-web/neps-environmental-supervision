package com.yuan.demo;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {
    private volatile int num = 0;

    public void add(){
        num = num+60;
    }

    public static void main(String[] args) {
        //一个线程用来给这个变量加值
        //另外一个线程用来看这个变量，如果这个变量》=60，就打印出来
        Test test = new Test();
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("增加方法开始运行了");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                test.add();
                System.out.println("增加方法运行完了");
            }
        }).start(); //开启线程
        //写一个死循环
        while(test.num==0){

        }
        System.out.println("num的值是："+test.num+"所有代码执行ok");
    }
}
