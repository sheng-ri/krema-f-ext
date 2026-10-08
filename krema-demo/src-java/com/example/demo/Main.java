package com.example.demo;

/**
 * krema-demo 的主类。
 *
 * 只做一件事：打一行握手标志，让 run 类 task 判得出「应用起来了」。
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.out.println("KREMA_APP_STARTED");
    }
}
