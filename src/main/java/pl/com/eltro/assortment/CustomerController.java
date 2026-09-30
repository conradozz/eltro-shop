package pl.com.eltro.assortment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    public CustomerController(
            CustomerRepository customerRepository,
            CustomerService customerService
    ) {
        this.customerRepository = customerRepository;
        this.customerService = customerService;
    }

    @GetMapping
    public List<Customer> getAll(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            return customerRepository.findAll();
        }
        return customerRepository.search(q);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable long id) {
        return customerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Customer> create(
            @RequestBody CreateCustomerRequest request
    ) {
        Customer created = customerService.create(request);

        return ResponseEntity
                .created(URI.create("/api/customers/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(
            @PathVariable long id,
            @RequestBody CreateCustomerRequest request
    ) {
        return ResponseEntity.ok(
                customerService.update(id, request)
        );
    }

    @PatchMapping("/{id}/discount")
    public ResponseEntity<Customer> updateDiscount(
            @PathVariable long id,
            @RequestBody UpdateDiscountRequest request
    ) {
        return ResponseEntity.ok(
                customerService.updateDiscount(id, request)
        );
    }
}