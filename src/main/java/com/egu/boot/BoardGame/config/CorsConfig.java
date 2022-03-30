package com.egu.boot.BoardGame.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer{

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		
		registry.addMapping("/**")
						.allowedOrigins("http://localhost:8082","http://localhost:8081","http://localhost:8083")
						.allowedOriginPatterns("*")
						.allowCredentials(true)
						.allowedMethods("*")
						.allowedHeaders("*")
						.exposedHeaders("*")
						.maxAge(3000);
	}
}
