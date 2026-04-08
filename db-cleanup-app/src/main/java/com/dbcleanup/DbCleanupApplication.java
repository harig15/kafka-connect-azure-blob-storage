package com.dbcleanup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the DB Cleanup Application.
 *
 * Features:
 *  - Loads cleanup policies from policies.yml at startup
 *  - Provides a REST API for on-demand policy execution
 *  - Persists execution history in MongoDB (embedded for PoC)
 *  - Serves a web dashboard at http://localhost:8080/dashboard
 */
@SpringBootApplication
@EnableAsync
public class DbCleanupApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbCleanupApplication.class, args);
    }
}
