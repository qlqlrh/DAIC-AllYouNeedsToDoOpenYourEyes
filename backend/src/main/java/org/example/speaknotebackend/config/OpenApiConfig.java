package org.example.speaknotebackend.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI speaknoteOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SpeakNote API")
                        .description("실시간 음성 기반 주석 시스템 API 문서")
                        .version("v1.0.0"));
    }
}
