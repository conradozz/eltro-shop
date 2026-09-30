package pl.com.eltro.assortment;

public record UserSummary(
        long id,
        String username,
        String role,
        boolean enabled
) {
}