package com.runab.smallsns;

import org.springframework.boot.SpringApplication;

public class TestSmallSnsApplication {

    public static void main(String[] args) {
        SpringApplication.from(SmallSnsApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
