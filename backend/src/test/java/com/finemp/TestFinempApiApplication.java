package com.finemp;

import org.springframework.boot.SpringApplication;

public class TestFinempApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinempApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
