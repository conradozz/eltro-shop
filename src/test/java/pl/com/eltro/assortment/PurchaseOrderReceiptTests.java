package pl.com.eltro.assortment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "app.bootstrap-admin.enabled=false"
})
@Testcontainers
class PurchaseOrderReceiptTests {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("order_receipt_test")
                    .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProductService productService;

    @Autowired
    DeliveryService deliveryService;

    @BeforeEach
    void prepare() {
        SecurityContextHolder.clearContext();

        jdbc.execute(
                "DROP TRIGGER IF EXISTS fail_purchase_order_event"
        );

        for (String table : List.of(
                "purchase_order_deliveries",
                "purchase_order_events",
                "purchase_orders",
                "delivery_corrections",
                "sale_revisions",
                "stock_movements",
                "sale_items",
                "sales",
                "products",
                "customers",
                "user_permissions",
                "users"
        )) {
            jdbc.update("DELETE FROM " + table);
        }

        jdbc.update(
                """
                INSERT INTO users (
                    id, username, password_hash, role, enabled
                )
                VALUES (101, 'test-admin', 'unused', 'ADMIN', TRUE)
                """
        );

        jdbc.update(
                """
                INSERT INTO products (
                    id, sku, name,
                    purchase_price_net, sale_price_net,
                    vat_rate, quantity, minimum_quantity
                )
                VALUES (1, 'TEST-A', 'Produkt A', 100, 125, 23, 0, 0)
                """
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-admin",
                        "unused",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                )
        );
    }

    @AfterEach
    void cleanUp() {
        try {
            jdbc.execute(
                    "DROP TRIGGER IF EXISTS fail_purchase_order_event"
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void receiptAllocatesOldestOrderFirstAndIgnoresDraft() {
        order(1, 3, "ORDERED");
        order(2, 4, "ORDERED");
        order(3, 5, "TO_ORDER");

        receive(5);

        assertOrder(1, 3, "RECEIVED");
        assertOrder(2, 2, "PARTIALLY_RECEIVED");
        assertOrder(3, 0, "TO_ORDER");

        assertEquals(5, stock());
        assertEquals(5, allocatedQuantity());
    }

    @Test
    void correctionRemovesUnallocatedSurplusBeforeReopeningOrder() {
        order(1, 3, "ORDERED");

        long delivery = receive(5);

        correct(delivery, 4);

        assertOrder(1, 3, "RECEIVED");
        assertEquals(4, stock());
        assertEquals(3, allocatedQuantity());

        correct(delivery, 2);

        assertOrder(1, 2, "PARTIALLY_RECEIVED");
        assertEquals(2, stock());
        assertEquals(2, allocatedQuantity());
    }

    @Test
    void correctionDoesNotRemoveAllocationFromAnotherDelivery() {
        order(1, 5, "ORDERED");

        long first = receive(2);
        receive(3);

        correct(first, 1);

        assertOrder(1, 4, "PARTIALLY_RECEIVED");
        assertEquals(4, stock());
        assertEquals(4, allocatedQuantity());
    }

    @Test
    void increasedDeliveryCompletesOrderAndRemarkOnlyCorrectionKeepsAllocation() {
        order(1, 3, "ORDERED");

        long delivery = receive(1);

        correct(delivery, 3);

        assertOrder(1, 3, "RECEIVED");
        assertEquals(3, stock());
        assertEquals(3, allocatedQuantity());

        correct(delivery, 3);

        assertOrder(1, 3, "RECEIVED");
        assertEquals(3, stock());
        assertEquals(3, allocatedQuantity());
    }

    @Test
    void allocationFailureRollsBackStockDeliveryAndOrderHistory() {
        order(1, 3, "ORDERED");

        // Trigger istnieje wyłącznie w bazie testowej.
        // Wymusza błąd podczas zapisywania historii zamówienia,
        // już po zmianie magazynu i zapisaniu powiązania dostawy.
        jdbc.execute(
                """
                CREATE TRIGGER fail_purchase_order_event
                BEFORE INSERT ON purchase_order_events
                FOR EACH ROW
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'Simulated order event failure'
                """
        );

        try {
            assertThrows(
                    DataAccessException.class,
                    () -> receive(2)
            );

            assertEquals(0, stock());
            assertOrder(1, 0, "ORDERED");

            for (String table : List.of(
                    "stock_movements",
                    "purchase_order_deliveries",
                    "purchase_order_events"
            )) {
                assertEquals(0, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table,
                        Integer.class
                ).intValue());
            }
        } finally {
            jdbc.execute(
                    "DROP TRIGGER IF EXISTS fail_purchase_order_event"
            );
        }
    }

    private void order(long id, int quantity, String status) {
        jdbc.update(
                """
                INSERT INTO purchase_orders (
                    id, product_id, product_name, sku,
                    quantity, status, ordered_at, created_by_user_id
                )
                VALUES (
                    ?, 1, 'Produkt A', 'TEST-A',
                    ?, ?, '2026-01-01 10:00:00', 101
                )
                """,
                id,
                quantity,
                status
        );
    }

    private long receive(int quantity) {
        productService.receiveDelivery(
                1,
                new ReceiveDeliveryRequest(
                        quantity,
                        new BigDecimal("100.00"),
                        new BigDecimal("25.00"),
                        "Test dostawy"
                )
        );

        return jdbc.queryForObject(
                "SELECT MAX(id) FROM stock_movements",
                Long.class
        );
    }

    private void correct(long id, int quantity) {
        deliveryService.update(
                id,
                new UpdateDeliveryRequest(
                        quantity,
                        new BigDecimal("100.00"),
                        new BigDecimal("25.00"),
                        "Test korekty"
                )
        );
    }

    private int stock() {
        return jdbc.queryForObject(
                "SELECT quantity FROM products WHERE id = 1",
                Integer.class
        );
    }

    private int allocatedQuantity() {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(allocated_quantity), 0)
                FROM purchase_order_deliveries
                """,
                Integer.class
        );
    }

    private void assertOrder(
            long id,
            int received,
            String status
    ) {
        assertEquals(received, jdbc.queryForObject(
                """
                SELECT received_quantity
                FROM purchase_orders
                WHERE id = ?
                """,
                Integer.class,
                id
        ).intValue());

        assertEquals(status, jdbc.queryForObject(
                "SELECT status FROM purchase_orders WHERE id = ?",
                String.class,
                id
        ));
    }
}