package org.example.speaknotebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Value("${custom.cors.allowed-origin}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
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

    // CORS 허용 설정 추가
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin)); // 환경변수에서 불러온 값 사용
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // 쿠키 포함 시 true

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 모든 경로에 적용
        return source;
    }
}
