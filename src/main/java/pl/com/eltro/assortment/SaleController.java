package pl.com.eltro.assortment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;
    private final SaleRepository saleRepository;
    private final SaleAvailabilityService saleAvailabilityService;

    public SaleController(
            SaleService saleService,
            SaleRepository saleRepository,
            SaleAvailabilityService saleAvailabilityService
    ) {
        this.saleService = saleService;
        this.saleRepository = saleRepository;
        this.saleAvailabilityService = saleAvailabilityService;
    }

    @PostMapping(params = "!preview")
    public ResponseEntity<Map<String, Long>> create(
            @RequestBody CreateSaleRequest request
    ) {
        long saleId = saleService.create(request);

        return ResponseEntity
                .created(URI.create("/api/sales/" + saleId))
                .body(Map.of("id", saleId));
    }

    @PostMapping(params = "preview=true")
    public SaleAvailabilityService.Availability checkAvailability(
            @RequestBody CreateSaleRequest request
    ) {
        return saleAvailabilityService.check(request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SaleDetails> update(
            @PathVariable long id,
            @RequestBody CreateSaleRequest request
    ) {
        return ResponseEntity.ok(
                saleService.update(id, request)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleDetails> getById(
            @PathVariable long id
    ) {
        return saleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    @GetMapping
    public List<SaleSummary> getRecent() {
        return saleRepository.findRecent();
    }
}