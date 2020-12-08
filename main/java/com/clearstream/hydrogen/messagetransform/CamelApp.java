package com.clearstream.hydrogen.messagetransform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.clearstream"})
        public class CamelApp extends  SpringBootServletInitializer {

    private final static String MANAGEMENT_DOMAIN = "com.clearstream.hydrogen.messagetransform";

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {

        System.setProperty("org.apache.camel.jmx.mbeanObjectDomainName", MANAGEMENT_DOMAIN);
        return application.sources(CamelApp.class);
    }


    // from command prompt run:   mvn spring-boot:run
    public static void main(String[] args) {
        SpringApplication.run(CamelApp.class, args);
    }
}
