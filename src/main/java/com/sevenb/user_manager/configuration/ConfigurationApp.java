package com.sevenb.user_manager.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ConfigurationApp {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }




}
