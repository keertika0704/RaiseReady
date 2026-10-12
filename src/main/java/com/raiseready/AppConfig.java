package com.raiseready;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** One place that says what "today" is, so code that needs the date can be tested with a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
