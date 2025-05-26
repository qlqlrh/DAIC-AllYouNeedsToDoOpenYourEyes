package org.example.speaknotebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",                         // 루트
                                "/api/pdf/**",               // PDF 관련 API
                                "/swagger-ui/**",            // Swagger UI HTML/CSS/JS 경로
                                "/v3/api-docs/**",           // OpenAPI JSON 경로
                                "/swagger-resources/**",     // (일부 swagger-ui 라이브러리)
                                "/webjars/**",               // swagger-ui에 필요한 js 라이브러리
                                "/ws/audio",
                                "/ws/annotation"
                        ).permitAll()                          // PDF 다운로드는 로그인 없이 허용
                        .anyRequest().authenticated()          // 나머지는 로그인 필요
                )
                .formLogin(withDefaults()); // 기본 로그인 폼 제공

        return http.build();
    }
}
