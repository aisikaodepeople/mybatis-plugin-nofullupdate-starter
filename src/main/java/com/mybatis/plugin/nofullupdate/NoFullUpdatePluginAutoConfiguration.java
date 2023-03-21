package com.mybatis.plugin.nofullupdate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "mybatis.nofullupdate.plugin", name = "enable", havingValue = "true")
public class NoFullUpdatePluginAutoConfiguration {

    @Bean
    public NoFullUpdateInterceptor noFullUpdateInterceptor() {
        return new NoFullUpdateInterceptor();
    }

}
