package pl.com.eltro.assortment;

public record DocumentIssuerSettings(
        String companyName,
        String nip,
        String street,
        String postalCode,
        String city,
        String email,
        String phone,
        String bankAccount
) {
}