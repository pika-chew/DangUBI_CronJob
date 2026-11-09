package com.pikachew.dangUBI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DangUbiApplication {

	public static void main(String[] args) {
		// Job이 실패하면 0이 아닌 종료 코드로 끝나야 K8s Job이 실패로 인식한다.
		System.exit(SpringApplication.exit(SpringApplication.run(DangUbiApplication.class, args)));
	}

}
