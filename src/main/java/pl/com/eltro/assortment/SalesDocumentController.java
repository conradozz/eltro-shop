package pl.com.eltro.assortment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
public class SalesDocumentController {

    private final SalesDocumentService service;

    public SalesDocumentController(
            SalesDocumentService service
    ) {
        this.service = service;
    }

    @PostMapping("/api/sales/{saleId}/documents")
    public ResponseEntity<SalesDocument> create(
            @PathVariable long saleId,
            @RequestBody CreateSalesDocumentRequest request
    ) {
        SalesDocument document = service.create(
                saleId,
                request
        );

        return ResponseEntity
                .created(URI.create(
                        "/api/documents/" + document.id()
                ))
                .body(document);
    }

    @GetMapping("/api/sales/{saleId}/documents")
    public List<SalesDocument> findBySaleId(
            @PathVariable long saleId
    ) {
        return service.findBySaleId(saleId);
    }

    @GetMapping("/api/documents/{id}")
    public ResponseEntity<SalesDocument> getById(
            @PathVariable long id
    ) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }
}