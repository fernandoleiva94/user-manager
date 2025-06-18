package com.sevenb.user_manager.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${user.manager.service.recipeFrontUrl}")
    private String recipeFrontUrl;


    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Ajusta la ruta según sea necesario
                .allowedOrigins(recipeFrontUrl)// Ajusta esto si tu frontend está en otro lugar
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowCredentials(true);
    }
}