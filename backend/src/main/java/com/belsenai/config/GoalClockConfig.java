package com.belsenai.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class GoalClockConfig {
    @Bean Clock reportingClock() { return Clock.systemUTC(); }
}
