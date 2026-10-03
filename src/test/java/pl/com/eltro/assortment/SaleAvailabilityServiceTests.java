package pl.com.eltro.assortment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleAvailabilityServiceTests {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    private SaleAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new SaleAvailabilityService(
                productRepository,
                customerRepository
        );
    }

    @Test
    void sufficientStockKeepsRequestedQuantityAndResolvesPrice() {
        givenProduct(1L, 5);

        CreateSaleRequest request = request(
                new SaleItemRequest(1L, 2, null, null)
        );

        SaleAvailabilityService.Availability result =
                service.check(request);

        assertFalse(result.hasShortages());
        assertTrue(result.canSellAnything());

        SaleAvailabilityService.AvailabilityLine line =
                result.lines().get(0);

        assertEquals(2, line.requestedQuantity());
        assertEquals(5, line.stockQuantity());
        assertEquals(2, line.availableQuantity());
        assertEquals(0, line.missingQuantity());
        assertAmount("125.00", line.unitSalePriceNet());
        assertAmount("0", line.discountPercent());

        SaleItemRequest availableItem =
                result.availableRequest().items().get(0);

        assertEquals(2, availableItem.quantity());
        assertAmount("125.00", availableItem.unitSalePriceNet());
        assertAmount("0", availableItem.discountPercent());

        assertEquals(
                "Test dostępności",
                result.availableRequest().remarks()
        );

        verify(productRepository).findById(1L);
        verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(customerRepository);
    }

    @Test
    void shortageReducesQuantityOnlyInProposedRequest() {
        givenProduct(1L, 2);

        CreateSaleRequest original = request(
                new SaleItemRequest(1L, 3, null, null)
        );

        SaleAvailabilityService.Availability result =
                service.check(original);

        assertTrue(result.hasShortages());
        assertTrue(result.canSellAnything());

        SaleAvailabilityService.AvailabilityLine line =
                result.lines().get(0);

        assertEquals(3, line.requestedQuantity());
        assertEquals(2, line.availableQuantity());
        assertEquals(1, line.missingQuantity());

        assertEquals(
                2,
                result.availableRequest().items().get(0).quantity()
        );

        // Oryginalny koszyk nie jest zmieniany.
        assertEquals(3, original.items().get(0).quantity());

        // Sprawdzenie dostępności tylko odczytuje produkt.
        verify(productRepository).findById(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void unavailableProductIsExcludedButAvailableProductRemains() {
        givenProduct(1L, 0);
        givenProduct(2L, 5);

        SaleAvailabilityService.Availability result =
                service.check(request(
                        new SaleItemRequest(1L, 1, null, null),
                        new SaleItemRequest(2L, 2, null, null)
                ));

        assertTrue(result.hasShortages());
        assertTrue(result.canSellAnything());
        assertEquals(2, result.lines().size());

        assertEquals(0, result.lines().get(0).availableQuantity());
        assertEquals(1, result.lines().get(0).missingQuantity());

        List<SaleItemRequest> availableItems =
                result.availableRequest().items();

        assertEquals(1, availableItems.size());
        assertEquals(
                Long.valueOf(2L),
                Long.valueOf(availableItems.get(0).productId())
        );
        assertEquals(2, availableItems.get(0).quantity());

        verify(productRepository).findById(1L);
        verify(productRepository).findById(2L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void emptyStockPreventsSellingAnything() {
        givenProduct(1L, 0);

        SaleAvailabilityService.Availability result =
                service.check(request(
                        new SaleItemRequest(1L, 4, null, null)
                ));

        assertTrue(result.hasShortages());
        assertFalse(result.canSellAnything());
        assertTrue(result.availableRequest().items().isEmpty());

        assertEquals(0, result.lines().get(0).availableQuantity());
        assertEquals(4, result.lines().get(0).missingQuantity());

        verify(productRepository).findById(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void repeatedProductCannotUseSameStockTwice() {
        givenProduct(1L, 3);

        SaleAvailabilityService.Availability result =
                service.check(request(
                        new SaleItemRequest(1L, 2, null, null),
                        new SaleItemRequest(1L, 2, null, null)
                ));

        assertTrue(result.hasShortages());
        assertTrue(result.canSellAnything());

        assertEquals(2, result.lines().get(0).availableQuantity());
        assertEquals(0, result.lines().get(0).missingQuantity());

        assertEquals(1, result.lines().get(1).availableQuantity());
        assertEquals(1, result.lines().get(1).missingQuantity());

        int totalAvailable = result.availableRequest().items()
                .stream()
                .mapToInt(SaleItemRequest::quantity)
                .sum();

        assertEquals(3, totalAvailable);

        // Jeden odczyt mimo dwóch pozycji tego samego produktu.
        verify(productRepository, times(1)).findById(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void explicitPriceAndDiscountArePreserved() {
        givenProduct(1L, 5);

        SaleAvailabilityService.Availability result =
                service.check(request(
                        new SaleItemRequest(
                                1L,
                                2,
                                new BigDecimal("99.50"),
                                new BigDecimal("7.50")
                        )
                ));

        SaleItemRequest item =
                result.availableRequest().items().get(0);

        assertAmount("99.50", item.unitSalePriceNet());
        assertAmount("7.50", item.discountPercent());
        assertEquals(2, item.quantity());
    }

    @Test
    void invalidPriceIsRejectedEvenWhenProductIsUnavailable() {
        givenProduct(1L, 0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.check(request(
                        new SaleItemRequest(
                                1L,
                                1,
                                new BigDecimal("-1.00"),
                                null
                        )
                ))
        );

        assertEquals(
                "Price must be non-negative "
                        + "with at most two decimal places",
                exception.getMessage()
        );

        verify(productRepository).findById(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void discountAboveOneHundredIsRejected() {
        givenProduct(1L, 5);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.check(request(
                        new SaleItemRequest(
                                1L,
                                1,
                                null,
                                new BigDecimal("100.01")
                        )
                ))
        );

        assertEquals(
                "Discount must be between 0 and 100 "
                        + "with at most two decimal places",
                exception.getMessage()
        );
    }

    @Test
    void zeroQuantityIsRejectedBeforeReadingProducts() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.check(request(
                        new SaleItemRequest(1L, 0, null, null)
                ))
        );

        assertEquals(
                "Quantity must be positive",
                exception.getMessage()
        );

        verifyNoInteractions(
                productRepository,
                customerRepository
        );
    }

    @Test
    void emptyBasketIsRejectedBeforeReadingProducts() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.check(new CreateSaleRequest(
                        null,
                        List.of(),
                        null
                ))
        );

        assertEquals(
                "Sale must contain at least one item",
                exception.getMessage()
        );

        verifyNoInteractions(
                productRepository,
                customerRepository
        );
    }

    @Test
    void missingProductIsRejected() {
        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.check(request(
                        new SaleItemRequest(999L, 1, null, null)
                ))
        );

        assertEquals(
                "Product does not exist",
                exception.getMessage()
        );
    }

    private void givenProduct(long id, int quantity) {
        Product product = new Product(
                id,
                "TEST-" + id,
                null,
                null,
                "Produkt " + id,
                "Bosch",
                null,
                "Akumulator",
                new BigDecimal("100.00"),
                new BigDecimal("125.00"),
                new BigDecimal("23.00"),
                quantity,
                2,
                null
        );

        when(productRepository.findById(id))
                .thenReturn(Optional.of(product));
    }

    private CreateSaleRequest request(SaleItemRequest... items) {
        return new CreateSaleRequest(
                null,
                List.of(items),
                "Test dostępności"
        );
    }

    private void assertAmount(
            String expected,
            BigDecimal actual
    ) {
        assertNotNull(actual);
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }
}