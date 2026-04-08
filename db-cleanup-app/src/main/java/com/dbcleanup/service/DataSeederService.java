package com.dbcleanup.service;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Seeds the embedded MongoDB with realistic sample data for PoC demonstration.
 *
 * Seeded collections (matching the policies in policies.yml):
 *   - orders:   purchase orders with status and createdAt
 *   - users:    user accounts with active flag and lastLoginAt
 *   - sessions: browser sessions with expiresAt
 *
 * Runs once on application startup. Skips seeding if data already exists.
 */
@Service
public class DataSeederService {

    private static final Logger log = LoggerFactory.getLogger(DataSeederService.class);
    private static final Random RANDOM = new Random(42);

    private final MongoTemplate mongoTemplate;

    public DataSeederService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        seedOrders();
        seedUsers();
        seedSessions();
        log.info("Sample data seeding complete.");
    }

    // -------------------------------------------------------------------------
    // orders collection
    // -------------------------------------------------------------------------

    private void seedOrders() {
        if (mongoTemplate.getCollection("orders").countDocuments() > 0) {
            log.info("orders collection already seeded, skipping.");
            return;
        }

        List<Document> docs = new ArrayList<>();
        String[] statuses = {"COMPLETED", "PENDING", "CANCELLED", "COMPLETED", "COMPLETED"};

        // 30 old completed orders (35-90 days old) - eligible for deletion
        for (int i = 1; i <= 30; i++) {
            int daysOld = 35 + RANDOM.nextInt(55);
            docs.add(new Document()
                    .append("orderId", "ORD-" + String.format("%04d", i))
                    .append("customerId", "CUST-" + (100 + RANDOM.nextInt(50)))
                    .append("status", "COMPLETED")
                    .append("totalAmount", 50 + RANDOM.nextInt(500))
                    .append("items", RANDOM.nextInt(5) + 1)
                    .append("createdAt", Date.from(Instant.now().minus(daysOld, ChronoUnit.DAYS)))
            );
        }

        // 15 recent orders (1-25 days old) - NOT eligible (under 30-day threshold)
        for (int i = 31; i <= 45; i++) {
            int daysOld = 1 + RANDOM.nextInt(25);
            docs.add(new Document()
                    .append("orderId", "ORD-" + String.format("%04d", i))
                    .append("customerId", "CUST-" + (100 + RANDOM.nextInt(50)))
                    .append("status", statuses[RANDOM.nextInt(statuses.length)])
                    .append("totalAmount", 50 + RANDOM.nextInt(500))
                    .append("items", RANDOM.nextInt(5) + 1)
                    .append("createdAt", Date.from(Instant.now().minus(daysOld, ChronoUnit.DAYS)))
            );
        }

        // 5 old PENDING orders - NOT eligible (wrong status for the policy)
        for (int i = 46; i <= 50; i++) {
            docs.add(new Document()
                    .append("orderId", "ORD-" + String.format("%04d", i))
                    .append("customerId", "CUST-" + (100 + RANDOM.nextInt(50)))
                    .append("status", "PENDING")
                    .append("totalAmount", 50 + RANDOM.nextInt(500))
                    .append("items", RANDOM.nextInt(5) + 1)
                    .append("createdAt", Date.from(Instant.now().minus(40 + RANDOM.nextInt(30), ChronoUnit.DAYS)))
            );
        }

        mongoTemplate.getCollection("orders").insertMany(docs);
        log.info("Seeded {} orders ({} eligible for cleanup)", docs.size(), 30);
    }

    // -------------------------------------------------------------------------
    // users collection
    // -------------------------------------------------------------------------

    private void seedUsers() {
        if (mongoTemplate.getCollection("users").countDocuments() > 0) {
            log.info("users collection already seeded, skipping.");
            return;
        }

        List<Document> docs = new ArrayList<>();

        // 20 inactive users with lastLoginAt > 90 days ago - eligible for deletion
        for (int i = 1; i <= 20; i++) {
            int daysOld = 95 + RANDOM.nextInt(200);
            docs.add(new Document()
                    .append("username", "user_inactive_" + i)
                    .append("email", "inactive" + i + "@example.com")
                    .append("active", false)
                    .append("registeredAt", Date.from(Instant.now().minus(daysOld + 30L, ChronoUnit.DAYS)))
                    .append("lastLoginAt", Date.from(Instant.now().minus(daysOld, ChronoUnit.DAYS)))
            );
        }

        // 25 active users - NOT eligible (active=true)
        for (int i = 21; i <= 45; i++) {
            int daysOld = RANDOM.nextInt(30);
            docs.add(new Document()
                    .append("username", "user_active_" + i)
                    .append("email", "active" + i + "@example.com")
                    .append("active", true)
                    .append("registeredAt", Date.from(Instant.now().minus(180L + RANDOM.nextInt(365), ChronoUnit.DAYS)))
                    .append("lastLoginAt", Date.from(Instant.now().minus(daysOld, ChronoUnit.DAYS)))
            );
        }

        // 10 inactive but recent users - NOT eligible (last login < 90 days ago)
        for (int i = 46; i <= 55; i++) {
            int daysOld = 30 + RANDOM.nextInt(55);
            docs.add(new Document()
                    .append("username", "user_recent_inactive_" + i)
                    .append("email", "recent_inactive" + i + "@example.com")
                    .append("active", false)
                    .append("registeredAt", Date.from(Instant.now().minus(180L, ChronoUnit.DAYS)))
                    .append("lastLoginAt", Date.from(Instant.now().minus(daysOld, ChronoUnit.DAYS)))
            );
        }

        mongoTemplate.getCollection("users").insertMany(docs);
        log.info("Seeded {} users ({} eligible for cleanup)", docs.size(), 20);
    }

    // -------------------------------------------------------------------------
    // sessions collection
    // -------------------------------------------------------------------------

    private void seedSessions() {
        if (mongoTemplate.getCollection("sessions").countDocuments() > 0) {
            log.info("sessions collection already seeded, skipping.");
            return;
        }

        List<Document> docs = new ArrayList<>();

        // 50 expired sessions (8-60 days old) - eligible for deletion
        for (int i = 1; i <= 50; i++) {
            int daysOld = 8 + RANDOM.nextInt(52);
            Instant expiredAt = Instant.now().minus(daysOld, ChronoUnit.DAYS);
            docs.add(new Document()
                    .append("sessionId", "sess-" + java.util.UUID.randomUUID())
                    .append("userId", "CUST-" + (100 + RANDOM.nextInt(100)))
                    .append("userAgent", "Mozilla/5.0 (compatible; PoC)")
                    .append("ipAddress", "192.168.1." + (1 + RANDOM.nextInt(254)))
                    .append("createdAt", Date.from(expiredAt.minus(1, ChronoUnit.HOURS)))
                    .append("expiresAt", Date.from(expiredAt))
            );
        }

        // 15 active sessions (not expired yet) - NOT eligible
        for (int i = 51; i <= 65; i++) {
            Instant expiresAt = Instant.now().plus(1 + RANDOM.nextInt(6), ChronoUnit.DAYS);
            docs.add(new Document()
                    .append("sessionId", "sess-" + java.util.UUID.randomUUID())
                    .append("userId", "CUST-" + (100 + RANDOM.nextInt(100)))
                    .append("userAgent", "Mozilla/5.0 (compatible; PoC)")
                    .append("ipAddress", "192.168.1." + (1 + RANDOM.nextInt(254)))
                    .append("createdAt", Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                    .append("expiresAt", Date.from(expiresAt))
            );
        }

        mongoTemplate.getCollection("sessions").insertMany(docs);
        log.info("Seeded {} sessions ({} eligible for cleanup)", docs.size(), 50);
    }
}
