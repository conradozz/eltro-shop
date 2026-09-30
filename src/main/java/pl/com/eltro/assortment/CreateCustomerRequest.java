package pl.com.eltro.assortment;

import java.math.BigDecimal;

public record CreateCustomerRequest(
        String firstName,
        String lastName,
        String companyName,
        String customerType,
        String nip,
        String regon,
        String street,
        String postalCode,
        String city,
        String phone,
        String email,
        BigDecimal discountPercent,
        String remarks
) {
}