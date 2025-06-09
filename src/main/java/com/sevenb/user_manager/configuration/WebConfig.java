package com.sevenb.user_manager.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Ajusta la ruta según sea necesario
                .allowedOrigins("http://localhost:3000")
                .allowedOrigins("https://d4bf-190-192-90-146.ngrok-free.app")// Ajusta esto si tu frontend está en otro lugar
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowCredentials(true);
    }
}