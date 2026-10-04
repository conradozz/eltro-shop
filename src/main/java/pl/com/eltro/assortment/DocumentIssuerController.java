package pl.com.eltro.assortment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/document-issuer")
public class DocumentIssuerController {

    private final DocumentIssuerService service;

    public DocumentIssuerController(
            DocumentIssuerService service
    ) {
        this.service = service;
    }

    @GetMapping
    public DocumentIssuerSettings getSettings() {
        return service.getSettings();
    }

    @PutMapping
    public DocumentIssuerSettings update(
            @RequestBody DocumentIssuerSettings request
    ) {
        return service.update(request);
    }
}