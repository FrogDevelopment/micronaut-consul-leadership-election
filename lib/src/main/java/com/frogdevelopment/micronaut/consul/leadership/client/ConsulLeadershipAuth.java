package com.frogdevelopment.micronaut.consul.leadership.client;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.http.annotation.FilterMatcher;

@Documented
@FilterMatcher
@Target({TYPE})
@Retention(RUNTIME)
public @interface ConsulLeadershipAuth {
}
