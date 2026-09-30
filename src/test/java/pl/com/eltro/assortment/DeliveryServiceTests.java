package pl.com.eltro.assortment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "app.bootstrap-admin.enabled=false"
})
@Testcontainers
class DeliveryServiceTests {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("delivery_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DeliveryService deliveryService;

    @SpyBean
    DeliveryRepository deliveryRepository;

    @BeforeEach
    void prepareDatabase() {
        SecurityContextHolder.clearContext();

        jdbc.update("DELETE FROM delivery_corrections");
        jdbc.update("DELETE FROM sale_revisions");
        jdbc.update("DELETE FROM stock_movements");
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM customers");
        jdbc.update("DELETE FROM users");

        // W tych testach nie logujemy się przez formularz.
        // Ustawiamy uwierzytelnienie bezpośrednio w SecurityContext.
        jdbc.update(
                """
                INSERT INTO users (
                    id, username, password_hash, role, enabled
                )
                VALUES (101, 'test-admin', 'unused-in-this-test', 'ADMIN', TRUE)
                """
        );

        jdbc.update(
                """
                INSERT INTO products (
                    id, sku, name,
                    purchase_price_net, sale_price_net,
                    vat_rate, quantity, minimum_quantity
                )
                VALUES (
                    1, 'TEST-DELIVERY', 'Produkt testowy',
                    100.00, 125.00,
                    23.00, 5, 0
                )
                """
        );

        jdbc.update(
                """
                INSERT INTO stock_movements (
                    id, product_id, quantity_change, movement_type,
                    unit_purchase_price_net, markup_percent,
                    unit_sale_price_net, remarks, created_by_user_id
                )
                VALUES (
                    10, 1, 5, 'DELIVERY',
                    100.00, 25.00,
                    125.00, 'Pierwotna dostawa', 101
                )
                """
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-admin",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void correctionChangesStockAndRecordsAuthor() {
        UpdateDeliveryRequest request = new UpdateDeliveryRequest(
                7,
                new BigDecimal("110.00"),
                new BigDecimal("25.00"),
                "Korekta do siedmiu sztuk"
        );

        deliveryService.update(10L, request);

        // Dostawa zwiększona z 5 do 7: magazyn rośnie o 2.
        assertEquals(7, productQuantity());
        assertEquals(7, deliveryQuantity());
        assertEquals(1, correctionCount());

        assertEquals(
                5,
                jdbc.queryForObject(
                        """
                        SELECT previous_quantity
                        FROM delivery_corrections
                        WHERE delivery_movement_id = 10
                        """,
                        Integer.class
                )
        );

        assertEquals(
                7,
                jdbc.queryForObject(
                        """
                        SELECT new_quantity
                        FROM delivery_corrections
                        WHERE delivery_movement_id = 10
                        """,
                        Integer.class
                )
        );

        assertEquals(
                101L,
                jdbc.queryForObject(
                        """
                        SELECT corrected_by_user_id
                        FROM delivery_corrections
                        WHERE delivery_movement_id = 10
                        """,
                        Long.class
                )
        );

        assertEquals(
                new BigDecimal("137.50"),
                jdbc.queryForObject(
                        """
                        SELECT unit_sale_price_net
                        FROM stock_movements
                        WHERE id = 10
                        """,
                        BigDecimal.class
                )
        );

        // Historyczna korekta dostawy nie zmienia bieżącego cennika.
        assertEquals(
                new BigDecimal("125.00"),
                jdbc.queryForObject(
                        "SELECT sale_price_net FROM products WHERE id = 1",
                        BigDecimal.class
                )
        );
    }

    @Test
    void correctionCannotMakeStockNegative() {
        // Z pięciu przyjętych sztuk trzy zostały już zdjęte z magazynu.
        jdbc.update("UPDATE products SET quantity = 2 WHERE id = 1");

        jdbc.update(
                """
                INSERT INTO stock_movements (
                    product_id, quantity_change, movement_type,
                    remarks, created_by_user_id
                )
                VALUES (1, -3, 'CORRECTION', 'Przygotowanie testu', 101)
                """
        );

        UpdateDeliveryRequest request = new UpdateDeliveryRequest(
                1,
                new BigDecimal("100.00"),
                new BigDecimal("25.00"),
                "Niedozwolone zmniejszenie dostawy"
        );

        // Zmiana dostawy z 5 na 1 wymaga odjęcia 4 sztuk,
        // a na magazynie są tylko 2.
        assertThrows(
                IllegalArgumentException.class,
                () -> deliveryService.update(10L, request)
        );

        assertEquals(2, productQuantity());
        assertEquals(5, deliveryQuantity());
        assertEquals(0, correctionCount());
        assertEquals("Pierwotna dostawa", deliveryRemarks());
    }

    @Test
    void errorAfterSavingCorrectionRollsBackAllChanges() {
        // Najpierw wykonujemy prawdziwy zapis korekty,
        // a następnie celowo zgłaszamy błąd.
        doAnswer(invocation -> {
            invocation.callRealMethod();

            throw new IllegalStateException(
                    "Simulated failure after saving correction"
            );
        }).when(deliveryRepository).saveCorrection(
                any(DeliveryRepository.DeliveryRow.class),
                any(UpdateDeliveryRequest.class),
                any(BigDecimal.class)
        );

        UpdateDeliveryRequest request = new UpdateDeliveryRequest(
                7,
                new BigDecimal("110.00"),
                new BigDecimal("25.00"),
                "Ta zmiana musi zostać wycofana"
        );

        assertThrows(
                IllegalStateException.class,
                () -> deliveryService.update(10L, request)
        );

        // Rollback musi cofnąć stan produktu, dostawę i historię korekty.
        assertEquals(5, productQuantity());
        assertEquals(5, deliveryQuantity());
        assertEquals(0, correctionCount());
        assertEquals("Pierwotna dostawa", deliveryRemarks());

        assertEquals(
                new BigDecimal("100.00"),
                jdbc.queryForObject(
                        """
                        SELECT unit_purchase_price_net
                        FROM stock_movements
                        WHERE id = 10
                        """,
                        BigDecimal.class
                )
        );

        assertEquals(
                new BigDecimal("125.00"),
                jdbc.queryForObject(
                        """
                        SELECT unit_sale_price_net
                        FROM stock_movements
                        WHERE id = 10
                        """,
                        BigDecimal.class
                )
        );
    }

    private int productQuantity() {
        return jdbc.queryForObject(
                "SELECT quantity FROM products WHERE id = 1",
                Integer.class
        );
    }

    private int deliveryQuantity() {
        return jdbc.queryForObject(
                "SELECT quantity_change FROM stock_movements WHERE id = 10",
                Integer.class
        );
    }

    private int correctionCount() {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM delivery_corrections
                WHERE delivery_movement_id = 10
                """,
                Integer.class
        );
    }

    private String deliveryRemarks() {
        return jdbc.queryForObject(
                "SELECT remarks FROM stock_movements WHERE id = 10",
                String.class
        );
    }
}