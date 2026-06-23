package com.javieraviles.rfqservice;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class RfqServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RfqServiceApplication.class, args);
	}

	/**
	 * Backed by Apache HttpClient so that HTTP PATCH is supported (the default
	 * {@code SimpleClientHttpRequestFactory} cannot issue PATCH requests).
	 */
	@Bean
	RestTemplate restTemplate() {
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
	}

}
