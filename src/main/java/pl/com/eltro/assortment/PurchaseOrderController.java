package pl.com.eltro.assortment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<PurchaseOrder> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PurchaseOrder get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<PurchaseOrder> create(
            @RequestBody PurchaseOrderRequest request
    ) {
        PurchaseOrder result = service.create(request);

        return ResponseEntity
                .created(URI.create("/api/purchase-orders/" + result.id()))
                .body(result);
    }

    @PutMapping("/{id}")
    public PurchaseOrder update(
            @PathVariable long id,
            @RequestBody PurchaseOrderRequest request
    ) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/ordered")
    public PurchaseOrder markOrdered(@PathVariable long id) {
        return service.markOrdered(id);
    }

    @PatchMapping("/{id}/cancel")
    public PurchaseOrder cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}/history")
    public List<PurchaseOrderEvent> history(@PathVariable long id) {
        return service.history(id);
    }
}