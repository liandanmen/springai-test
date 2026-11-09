package org.example.springaitest.test;

import reactor.core.publisher.Flux;

public class FluxWarmup {
    public static void main(String[] args) {
        System.out.println("===== 第一段：只声明，不订阅 =====");
        Flux.just("a", "b", "c")
                .map(s -> {
                    System.out.println("  [map 执行] " + s);
                    return s.toUpperCase();
                });
        System.out.println("===== 声明完了 =====");

        System.out.println();
        System.out.println("===== 第二段：加了 subscribe =====");
        Flux.just("a", "b", "c")
                .map(s -> {
                    System.out.println("  [map 执行] " + s);
                    return s.toUpperCase();
                })
                .subscribe(s -> System.out.println("  [收到] " + s));
    }
}