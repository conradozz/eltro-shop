package pl.com.eltro.assortment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoModeController {

    private final boolean enabled;

    public DemoModeController(
            @Value("${app.demo.enabled:false}") boolean enabled
    ) {
        this.enabled = enabled;
    }

    @GetMapping("/api/demo-mode")
    public ResponseEntity<DemoModeSettings> getSettings() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new DemoModeSettings(enabled));
    }

    public record DemoModeSettings(boolean enabled) {
    }
}