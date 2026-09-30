package pl.com.eltro.assortment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductService productService;

    public ProductController(
            ProductRepository productRepository,
            ProductService productService
    ) {
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getAll(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            return productRepository.findAll();
        }

        return productRepository.search(q);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Product> create(
            @RequestBody CreateProductRequest request
    ) {
        Product created = productService.create(request);

        return ResponseEntity
                .created(URI.create("/api/products/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/stock")
    public ResponseEntity<Product> changeStock(
            @PathVariable long id,
            @RequestBody StockChangeRequest request
    ) {
        return ResponseEntity.ok(
                productService.changeStock(id, request)
        );
    }

    @PatchMapping("/{id}/prices")
    public ResponseEntity<Product> updatePrices(
            @PathVariable long id,
            @RequestBody UpdateProductPriceRequest request
    ) {
        return ResponseEntity.ok(
                productService.updatePrices(id, request)
        );
    }

    @PatchMapping("/{id}/details")
    public ResponseEntity<Product> updateDetails(
            @PathVariable long id,
            @RequestBody UpdateProductDetailsRequest request
    ) {
        return ResponseEntity.ok(
                productService.updateDetails(id, request)
        );
    }

    @PostMapping("/{id}/deliveries")
    public ResponseEntity<Product> receiveDelivery(
            @PathVariable long id,
            @RequestBody ReceiveDeliveryRequest request
    ) {
        return ResponseEntity.ok(
                productService.receiveDelivery(id, request)
        );
    }
}