package pl.com.eltro.assortment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "app.bootstrap-admin.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class SalesReportTests {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("sales_report_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void prepare() {
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
                VALUES
                    (101, 'seller-a', 'unused', 'SELLER', TRUE),
                    (102, 'seller-b', 'unused', 'SELLER', TRUE),
                    (103, 'report-admin', 'unused', 'ADMIN', TRUE)
                """
        );

        jdbc.update(
                """
                INSERT INTO products (
                    id, sku, name, purchase_price_net, sale_price_net,
                    vat_rate, quantity, minimum_quantity
                )
                VALUES (
                    1, 'REPORT-A', 'Produkt raportowy',
                    100, 125, 23, 0, 0
                )
                """
        );

        // Daty w bazie są w UTC.
        // Pierwsza sprzedaż to w Polsce 2 stycznia, godz. 00:30.
        // Trzecia sprzedaż to w Polsce już 3 stycznia.
        jdbc.update(
                """
                INSERT INTO sales (
                    id, sold_at, created_by_user_id
                )
                VALUES
                    (1, '2026-01-01 23:30:00', 101),
                    (2, '2026-01-02 12:00:00', 102),
                    (3, '2026-01-02 23:30:00', 101)
                """
        );

        jdbc.update(
                """
                INSERT INTO sale_items (
                    sale_id, product_id, product_name, quantity,
                    unit_purchase_price_net, unit_sale_price_net,
                    discount_percent, vat_rate, active
                )
                VALUES
                    (1, 1, 'Produkt', 2, 100, 125, 10, 23, TRUE),
                    (1, 1, 'Stara wersja', 20, 100, 125, 0, 23, FALSE),
                    (2, 1, 'Produkt', 1, 100, 100, 0, 23, TRUE),
                    (3, 1, 'Produkt', 1, 100, 10, 0, 23, TRUE)
                """
        );
    }

    @Test
    @WithMockUser(username = "seller-a", roles = "SELLER")
    void sellerSeesOnlyOwnSalesUsingPolishDateAndActiveItems()
            throws Exception {

        mvc.perform(get("/api/reports/sales")
                        .param("from", "2026-01-02")
                        .param("to", "2026-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(101))
                .andExpect(jsonPath("$.totals.saleCount").value(1))
                .andExpect(jsonPath("$.totals.quantity").value(2))
                .andExpect(jsonPath("$.totals.totalNet").value(225.00))
                .andExpect(jsonPath("$.totals.totalGross").value(276.75))
                .andExpect(jsonPath("$.periods[0].period")
                        .value("2026-01-02"));
    }

    @Test
    @WithMockUser(username = "seller-a", roles = "SELLER")
    void sellerCannotRequestAnotherEmployeesReportOrDashboard()
            throws Exception {

        mvc.perform(get("/api/reports/sales")
                        .param("from", "2026-01-02")
                        .param("to", "2026-01-02")
                        .param("userId", "102"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/reports/sales/dashboard")
                        .param("userId", "102"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/reports/sales/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "report-admin", roles = "ADMIN")
    void adminSeesWholeShopAndCanFilterEmployee() throws Exception {
        mvc.perform(get("/api/reports/sales")
                        .param("from", "2026-01-02")
                        .param("to", "2026-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.saleCount").value(2))
                .andExpect(jsonPath("$.totals.quantity").value(3))
                .andExpect(jsonPath("$.totals.totalNet").value(325.00))
                .andExpect(jsonPath("$.totals.totalGross").value(399.75));

        mvc.perform(get("/api/reports/sales")
                        .param("from", "2026-01-02")
                        .param("to", "2026-01-02")
                        .param("userId", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.totalNet").value(100.00));
    }

    @Test
    @WithMockUser(username = "report-admin", roles = "ADMIN")
    void reportGroupsByMonthAndYear() throws Exception {
        for (String grouping : List.of("MONTH", "YEAR")) {
            String expectedPeriod = grouping.equals("MONTH")
                    ? "2026-01"
                    : "2026";

            mvc.perform(get("/api/reports/sales")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31")
                            .param("grouping", grouping))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periods.length()").value(1))
                    .andExpect(jsonPath("$.periods[0].period")
                            .value(expectedPeriod))
                    .andExpect(jsonPath("$.totals.totalNet")
                            .value(335.00));
        }
    }

    @Test
    @WithMockUser(username = "seller-a", roles = "SELLER")
    void emptyPeriodReturnsZeroTotals() throws Exception {
        mvc.perform(get("/api/reports/sales")
                        .param("from", "2025-01-01")
                        .param("to", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.saleCount").value(0))
                .andExpect(jsonPath("$.totals.quantity").value(0))
                .andExpect(jsonPath("$.totals.totalGross").value(0));
    }

    @Test
    @WithMockUser(username = "report-admin", roles = "ADMIN")
    void invalidDatesAndGroupingAreRejected() throws Exception {
        mvc.perform(get("/api/reports/sales")
                        .param("from", "2026-02-01")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/reports/sales")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-02")
                        .param("grouping", "INVALID"))
                .andExpect(status().isBadRequest());
    }
}