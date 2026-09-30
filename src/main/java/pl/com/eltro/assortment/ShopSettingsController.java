package pl.com.eltro.assortment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/shop-settings")
public class ShopSettingsController {

    private final ShopSettingsRepository shopSettingsRepository;

    public ShopSettingsController(
            ShopSettingsRepository shopSettingsRepository
    ) {
        this.shopSettingsRepository = shopSettingsRepository;
    }

    @GetMapping
    public Settings getSettings() {
        return new Settings(
                shopSettingsRepository.getDefaultMarkupPercent()
        );
    }

    public record Settings(BigDecimal defaultMarkupPercent) {
    }
}