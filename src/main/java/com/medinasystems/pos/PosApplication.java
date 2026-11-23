package com.medinasystems.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the point of sale backend. The same artifact runs as the
 * central node or as a store node, depending on the active profile.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosApplication.class, args);
    }
}
