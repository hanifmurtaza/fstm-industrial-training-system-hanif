package com.example.itsystem;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ItsystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(ItsystemApplication.class, args);
	}

	// ✅ 注册 Thymeleaf Layout Dialect（用于支持 layout:decorator）
	@Bean
	public LayoutDialect layoutDialect() {
		return new LayoutDialect();
	}
}
