package com.example.authclient.spring;

import com.example.authclient.AuthClientConfig;
import com.example.authclient.BearerTokenFilter;
import javax.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Optional Spring Boot 2.x wiring for the legacy library. Add the dependency, set the "auth.client.*" properties,
 * and the bearer-token filter is registered for you (all URLs, first in the chain).
 *
 * Only active when Spring Boot 2.x with a servlet web application is present, so plain servlet / WAR applications
 * that do not use Spring Boot never load this class and keep wiring the filter by hand (web.xml).
 *
 * To take over the wiring yourself, define your own bean named "bearerTokenFilter"; this configuration then backs off.
 * To switch it off (tests), set auth.client.enabled=false.
 */
@Configuration
@ConditionalOnClass({Filter.class, FilterRegistrationBean.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "auth.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LegacyAuthAutoConfiguration {

    @Bean(name = "bearerTokenFilter")
    @ConditionalOnMissingBean(name = "bearerTokenFilter")
    public FilterRegistrationBean<BearerTokenFilter> bearerTokenFilter(final Environment environment) {
        // Reads auth.client.<name> from the Spring Environment: application.yml/.properties, environment variables
        // (AUTH_CLIENT_CLIENT_SECRET), system properties, config server ... whatever the application already uses.
        AuthClientConfig config = AuthClientConfig.from(new AuthClientConfig.Source() {
            @Override
            public String get(String name) {
                return environment.getProperty("auth.client." + name);
            }
        }).validate();    // fails at startup with a clear message when a required property is missing

        FilterRegistrationBean<BearerTokenFilter> registration =
                new FilterRegistrationBean<BearerTokenFilter>(new BearerTokenFilter(config));
        registration.setName("bearerTokenFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
