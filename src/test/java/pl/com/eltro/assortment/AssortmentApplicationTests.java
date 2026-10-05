package pl.com.eltro.assortment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.flyway.baseline-on-migrate=false",
		"app.bootstrap-admin.enabled=false"
})
@Testcontainers
class AssortmentApplicationTests {

	private static final long SELLER_ID = 101L;
	private static final long ADMIN_ID = 102L;

	@Container
	static final MySQLContainer<?> mysql =
			new MySQLContainer<>("mysql:8.0.36")
					.withDatabaseName("assortment_test");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SaleService saleService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@SpyBean
	private ProductRepository productRepository;

	@BeforeEach
	void prepareDatabase() {
		SecurityContextHolder.clearContext();

		// Najpierw usuwamy dane odwołujące się do użytkowników.
		jdbc.update("DELETE FROM sale_revisions");
		jdbc.update("DELETE FROM stock_movements");
		jdbc.update("DELETE FROM sale_items");
		jdbc.update("DELETE FROM sales");
		jdbc.update("DELETE FROM products");
		jdbc.update("DELETE FROM customers");
		jdbc.update("DELETE FROM user_permissions");
		jdbc.update("DELETE FROM users");

		String passwordHash = passwordEncoder.encode(
				"Test-only-password-123!"
		);

		jdbc.update(
				"""
                INSERT INTO users (
                    id, username, password_hash, role, enabled
                )
                VALUES (?, ?, ?, ?, TRUE)
                """,
				SELLER_ID,
				"test-seller",
				passwordHash,
				"SELLER"
		);

		jdbc.update(
				"""
                INSERT INTO users (
                    id, username, password_hash, role, enabled
                )
                VALUES (?, ?, ?, ?, TRUE)
                """,
				ADMIN_ID,
				"test-admin",
				passwordHash,
				"ADMIN"
		);

		jdbc.update("""
                INSERT INTO products (
                    id, sku, name, purchase_price_net, sale_price_net,
                    vat_rate, quantity, minimum_quantity
                )
                VALUES
                    (1, 'TEST-A', 'Produkt A', 100.00, 125.00, 23.00, 5, 0),
                    (2, 'TEST-B', 'Produkt B', 100.00, 125.00, 23.00, 0, 0)
                """);

		authenticateAs("test-seller", "SELLER");
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void failedSecondItemRollsBackWholeSale() {
		CreateSaleRequest request = new CreateSaleRequest(
				null,
				List.of(
						new SaleItemRequest(1L, 1, null, null),
						new SaleItemRequest(2L, 1, null, null)
				),
				"Test rollbacku"
		);

		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> saleService.create(request)
		);

		assertEquals(
				"Insufficient stock for TEST-B",
				exception.getMessage()
		);

		assertEquals(5, quantityOf(1));
		assertEquals(0, quantityOf(2));
		assertEquals(0, countRows("sales"));
		assertEquals(0, countRows("sale_items"));
		assertEquals(0, countRows("stock_movements"));
	}

	@Test
	void successfulSaleDecreasesStockAndRecordsMovement() {
		CreateSaleRequest request = new CreateSaleRequest(
				null,
				List.of(new SaleItemRequest(1L, 2, null, null)),
				"Poprawna sprzedaż"
		);

		long saleId = saleService.create(request);

		assertEquals(3, quantityOf(1));
		assertEquals(0, quantityOf(2));
		assertEquals(1, countRows("sales"));
		assertEquals(1, countRows("sale_items"));
		assertEquals(1, countRows("stock_movements"));

		int soldQuantity = jdbc.queryForObject(
				"SELECT quantity FROM sale_items WHERE sale_id = ?",
				Integer.class,
				saleId
		);

		int quantityChange = jdbc.queryForObject(
				"""
                SELECT sm.quantity_change
                FROM stock_movements sm
                JOIN sale_items si ON si.id = sm.sale_item_id
                WHERE si.sale_id = ?
                """,
				Integer.class,
				saleId
		);

		assertEquals(2, soldQuantity);
		assertEquals(-2, quantityChange);

		assertEquals(SELLER_ID, saleAuthor(saleId));

		long movementAuthor = jdbc.queryForObject(
				"""
                SELECT sm.created_by_user_id
                FROM stock_movements sm
                JOIN sale_items si ON si.id = sm.sale_item_id
                WHERE si.sale_id = ?
                """,
				Long.class,
				saleId
		);

		assertEquals(SELLER_ID, movementAuthor);
	}

	@Test
	void concurrentSalesCannotSellTheLastItemTwice() throws Exception {
		jdbc.update("UPDATE products SET quantity = 1 WHERE id = 1");

		CountDownLatch bothReady = new CountDownLatch(2);
		CountDownLatch allowUpdate = new CountDownLatch(1);

		doAnswer(invocation -> {
			bothReady.countDown();

			if (!allowUpdate.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException(
						"Timed out waiting to update stock"
				);
			}

			return invocation.callRealMethod();
		}).when(productRepository).changeQuantity(1L, -1);

		CreateSaleRequest request = new CreateSaleRequest(
				null,
				List.of(new SaleItemRequest(1L, 1, null, null)),
				"Sprzedaż ostatniej sztuki"
		);

		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<Boolean> first = executor.submit(
					() -> attemptSale(request)
			);

			Future<Boolean> second = executor.submit(
					() -> attemptSale(request)
			);

			assertTrue(
					bothReady.await(20, TimeUnit.SECONDS),
					"Obie sprzedaże powinny dotrzeć do zmiany stanu"
			);

			allowUpdate.countDown();

			boolean firstSucceeded = first.get(30, TimeUnit.SECONDS);
			boolean secondSucceeded = second.get(30, TimeUnit.SECONDS);

			int successfulSales =
					(firstSucceeded ? 1 : 0)
							+ (secondSucceeded ? 1 : 0);

			assertEquals(
					1,
					successfulSales,
					"Dokładnie jedna sprzedaż powinna się udać"
			);

			assertEquals(0, quantityOf(1));
			assertEquals(1, countRows("sales"));
			assertEquals(1, countRows("sale_items"));
			assertEquals(1, countRows("stock_movements"));

			int soldQuantity = jdbc.queryForObject(
					"SELECT SUM(quantity) FROM sale_items",
					Integer.class
			);

			int stockChange = jdbc.queryForObject(
					"SELECT SUM(quantity_change) FROM stock_movements",
					Integer.class
			);

			assertEquals(1, soldQuantity);
			assertEquals(-1, stockChange);

			long author = jdbc.queryForObject(
					"SELECT created_by_user_id FROM sales",
					Long.class
			);

			assertEquals(SELLER_ID, author);
		} finally {
			allowUpdate.countDown();
			executor.shutdownNow();

			assertTrue(
					executor.awaitTermination(10, TimeUnit.SECONDS),
					"Wątki testu powinny zakończyć pracę"
			);
		}
	}

	@Test
	void successfulCorrectionUpdatesStockAndSavesRevision() {
		long saleId = saleService.create(new CreateSaleRequest(
				null,
				List.of(new SaleItemRequest(1L, 2, null, null)),
				"Pierwotna sprzedaż"
		));

		assertEquals(3, quantityOf(1));

		// Sprzedawca utworzył sprzedaż, administrator ją koryguje.
		authenticateAs("test-admin", "ADMIN");

		SaleDetails updated = saleService.update(
				saleId,
				new CreateSaleRequest(
						null,
						List.of(new SaleItemRequest(1L, 3, null, null)),
						"Korekta do trzech sztuk"
				)
		);

		assertEquals(saleId, updated.id());
		assertEquals("Korekta do trzech sztuk", updated.remarks());
		assertEquals(1, updated.items().size());
		assertEquals(3, updated.items().get(0).quantity());

		assertEquals(2, quantityOf(1));
		assertEquals(1, countRows("sales"));
		assertEquals(2, countRows("sale_items"));
		assertEquals(3, countRows("stock_movements"));
		assertEquals(1, countRows("sale_revisions"));

		int activeItems = jdbc.queryForObject(
				"""
                SELECT COUNT(*) FROM sale_items
                WHERE sale_id = ? AND active = TRUE
                """,
				Integer.class,
				saleId
		);

		int inactiveItems = jdbc.queryForObject(
				"""
                SELECT COUNT(*) FROM sale_items
                WHERE sale_id = ? AND active = FALSE
                """,
				Integer.class,
				saleId
		);

		int stockChange = jdbc.queryForObject(
				"""
                SELECT SUM(quantity_change)
                FROM stock_movements
                WHERE product_id = 1
                """,
				Integer.class
		);

		assertEquals(1, activeItems);
		assertEquals(1, inactiveItems);
		assertEquals(-3, stockChange);

		String previousQuantity = jdbc.queryForObject(
				"""
                SELECT JSON_UNQUOTE(
                    JSON_EXTRACT(previous_snapshot, '$.items[0].quantity')
                )
                FROM sale_revisions
                WHERE sale_id = ?
                """,
				String.class,
				saleId
		);

		String newQuantity = jdbc.queryForObject(
				"""
                SELECT JSON_UNQUOTE(
                    JSON_EXTRACT(new_snapshot, '$.items[0].quantity')
                )
                FROM sale_revisions
                WHERE sale_id = ?
                """,
				String.class,
				saleId
		);

		assertEquals("2", previousQuantity);
		assertEquals("3", newQuantity);

		// Korekta nie zmienia autora utworzenia sprzedaży.
		assertEquals(SELLER_ID, saleAuthor(saleId));

		long correctionAuthor = jdbc.queryForObject(
				"""
                SELECT corrected_by_user_id
                FROM sale_revisions
                WHERE sale_id = ?
                """,
				Long.class,
				saleId
		);

		assertEquals(ADMIN_ID, correctionAuthor);

		int sellerMovements = jdbc.queryForObject(
				"""
                SELECT COUNT(*)
                FROM stock_movements
                WHERE created_by_user_id = ?
                """,
				Integer.class,
				SELLER_ID
		);

		int adminMovements = jdbc.queryForObject(
				"""
                SELECT COUNT(*)
                FROM stock_movements
                WHERE created_by_user_id = ?
                """,
				Integer.class,
				ADMIN_ID
		);

		assertEquals(1, sellerMovements);
		assertEquals(2, adminMovements);
	}

	@Test
	void failedCorrectionRestoresPreviousSaleAndStock() {
		long saleId = saleService.create(new CreateSaleRequest(
				null,
				List.of(new SaleItemRequest(1L, 2, null, null)),
				"Pierwotna sprzedaż"
		));

		Long originalItemId = jdbc.queryForObject(
				"SELECT id FROM sale_items WHERE sale_id = ?",
				Long.class,
				saleId
		);

		authenticateAs("test-admin", "ADMIN");

		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> saleService.update(
						saleId,
						new CreateSaleRequest(
								null,
								List.of(
										new SaleItemRequest(1L, 3, null, null),
										new SaleItemRequest(2L, 1, null, null)
								),
								"Nieudana korekta"
						)
				)
		);

		assertEquals(
				"Insufficient stock for TEST-B",
				exception.getMessage()
		);

		assertEquals(3, quantityOf(1));
		assertEquals(0, quantityOf(2));
		assertEquals(1, countRows("sales"));
		assertEquals(1, countRows("sale_items"));
		assertEquals(1, countRows("stock_movements"));
		assertEquals(0, countRows("sale_revisions"));

		String remarks = jdbc.queryForObject(
				"SELECT remarks FROM sales WHERE id = ?",
				String.class,
				saleId
		);

		Long remainingItemId = jdbc.queryForObject(
				"""
                SELECT id FROM sale_items
                WHERE sale_id = ? AND active = TRUE
                """,
				Long.class,
				saleId
		);

		int soldQuantity = jdbc.queryForObject(
				"""
                SELECT quantity FROM sale_items
                WHERE sale_id = ? AND active = TRUE
                """,
				Integer.class,
				saleId
		);

		int stockChange = jdbc.queryForObject(
				"""
                SELECT quantity_change FROM stock_movements
                WHERE product_id = 1
                """,
				Integer.class
		);

		long movementAuthor = jdbc.queryForObject(
				"""
                SELECT created_by_user_id
                FROM stock_movements
                WHERE product_id = 1
                """,
				Long.class
		);

		assertEquals("Pierwotna sprzedaż", remarks);
		assertEquals(originalItemId, remainingItemId);
		assertEquals(2, soldQuantity);
		assertEquals(-2, stockChange);

		assertEquals(SELLER_ID, saleAuthor(saleId));
		assertEquals(SELLER_ID, movementAuthor);
	}

	@Test
	void saleWithoutAuthenticationIsRejected() {
		SecurityContextHolder.clearContext();

		CreateSaleRequest request = new CreateSaleRequest(
				null,
				List.of(new SaleItemRequest(1L, 1, null, null)),
				"Próba bez zalogowania"
		);

		assertThrows(
				AccessDeniedException.class,
				() -> saleService.create(request)
		);

		assertEquals(5, quantityOf(1));
		assertEquals(0, countRows("sales"));
		assertEquals(0, countRows("sale_items"));
		assertEquals(0, countRows("stock_movements"));
	}

	private void authenticateAs(String username, String role) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();

		context.setAuthentication(
				new UsernamePasswordAuthenticationToken(
						username,
						null,
						List.of(
								new SimpleGrantedAuthority("ROLE_" + role)
						)
				)
		);

		SecurityContextHolder.setContext(context);
	}

	private boolean attemptSale(CreateSaleRequest request) {
		// Każdy wątek otrzymuje własny kontekst użytkownika.
		authenticateAs("test-seller", "SELLER");

		try {
			saleService.create(request);
			return true;
		} catch (IllegalArgumentException exception) {
			if (!"Insufficient stock for TEST-A".equals(
					exception.getMessage()
			)) {
				throw exception;
			}

			return false;
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private long saleAuthor(long saleId) {
		return jdbc.queryForObject(
				"""
                SELECT created_by_user_id
                FROM sales
                WHERE id = ?
                """,
				Long.class,
				saleId
		);
	}

	private int quantityOf(long productId) {
		return jdbc.queryForObject(
				"SELECT quantity FROM products WHERE id = ?",
				Integer.class,
				productId
		);
	}

	private int countRows(String table) {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM " + table,
				Integer.class
		);
	}
}