package pl.com.eltro.assortment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class DocumentIssuerService {

    private final DocumentIssuerRepository repository;

    public DocumentIssuerService(
            DocumentIssuerRepository repository
    ) {
        this.repository = repository;
    }

    public DocumentIssuerSettings getSettings() {
        return repository.getSettings();
    }

    @Transactional
    public DocumentIssuerSettings update(
            DocumentIssuerSettings request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Issuer settings are required"
            );
        }

        String companyName = text(
                request.companyName(),
                200,
                "Company name"
        );

        if (companyName == null) {
            throw new IllegalArgumentException(
                    "Company name is required"
            );
        }

        String nip = text(request.nip(), 10, "NIP");

        if (nip != null && !nip.matches("[0-9]{10}")) {
            throw new IllegalArgumentException(
                    "NIP must contain exactly 10 digits"
            );
        }

        String email = text(request.email(), 200, "Email");

        if (email != null
                && !email.matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
        )) {
            throw new IllegalArgumentException(
                    "Invalid email address"
            );
        }

        String bankAccount = normalizeBankAccount(
                request.bankAccount()
        );

        DocumentIssuerSettings settings =
                new DocumentIssuerSettings(
                        companyName,
                        nip,
                        text(request.street(), 200, "Street"),
                        text(request.postalCode(), 10, "Postal code"),
                        text(request.city(), 100, "City"),
                        email,
                        text(request.phone(), 30, "Phone"),
                        bankAccount
                );

        repository.update(settings);
        return repository.getSettings();
    }

    private String text(
            String value,
            int maximumLength,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " is too long"
            );
        }

        return normalized;
    }

    private String normalizeBankAccount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);

        boolean polishAccount =
                normalized.matches("[0-9]{26}");

        boolean ibanFormat =
                normalized.matches(
                        "[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}"
                );

        if (!polishAccount && !ibanFormat) {
            throw new IllegalArgumentException(
                    "Provide a 26-digit account number "
                            + "or an IBAN"
            );
        }

        return normalized;
    }
}