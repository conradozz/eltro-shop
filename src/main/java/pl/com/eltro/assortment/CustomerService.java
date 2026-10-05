package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final JdbcTemplate jdbcTemplate;

    public CustomerService(
            CustomerRepository customerRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.customerRepository = customerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Customer create(CreateCustomerRequest request) {
        BigDecimal discount = validateAndGetDiscount(request);

        if (discount.signum() != 0 && !canChangeDiscount()) {
            throw new AccessDeniedException(
                    "Permission to change customer discounts is required"
            );
        }

        long id = customerRepository.create(request, discount);
        return customerRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Customer update(
            long customerId,
            CreateCustomerRequest request
    ) {
        BigDecimal discount = validateAndGetDiscount(request);

        Customer previous = lockAndFindCustomer(customerId);

        if (!canChangeDiscount()) {
            if (request.discountPercent() != null
                    && discount.compareTo(previous.discountPercent()) != 0) {
                throw new AccessDeniedException(
                        "Permission to change customer discounts is required"
                );
            }

            discount = previous.discountPercent();
        }

        customerRepository.update(
                customerId,
                request,
                discount
        );

        return customerRepository.findById(customerId).orElseThrow();
    }

    @Transactional
    public Customer updateDiscount(
            long customerId,
            UpdateDiscountRequest request
    ) {
        if (!canChangeDiscount()) {
            throw new AccessDeniedException(
                    "Permission to change customer discounts is required"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Discount data is required"
            );
        }

        BigDecimal discount = request.discountPercent();
        validateDiscount(discount);

        lockAndFindCustomer(customerId);

        customerRepository.updateDiscount(customerId, discount);

        return customerRepository.findById(customerId).orElseThrow();
    }

    private Customer lockAndFindCustomer(long customerId) {
        boolean exists = !jdbcTemplate.query(
                """
                SELECT id
                FROM customers
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getLong("id"),
                customerId
        ).isEmpty();

        if (!exists) {
            throw new IllegalArgumentException(
                    "Customer does not exist"
            );
        }

        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer does not exist"
                ));
    }

    private boolean canChangeDiscount() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                                || "CUSTOMER_DISCOUNT_EDIT".equals(
                                authority.getAuthority()
                        )
                );
    }

    private BigDecimal validateAndGetDiscount(
            CreateCustomerRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Customer data is required"
            );
        }

        if ("COMPANY".equals(request.customerType())) {
            if (request.companyName() == null
                    || request.companyName().isBlank()) {
                throw new IllegalArgumentException(
                        "Company name is required"
                );
            }
        } else if ("PERSON".equals(request.customerType())) {
            if (request.firstName() == null
                    || request.firstName().isBlank()
                    || request.lastName() == null
                    || request.lastName().isBlank()) {
                throw new IllegalArgumentException(
                        "First and last name are required"
                );
            }

            if (request.companyName() != null
                    || request.nip() != null
                    || request.regon() != null) {
                throw new IllegalArgumentException(
                        "Private customer cannot have "
                                + "company name, NIP or REGON"
                );
            }
        } else {
            throw new IllegalArgumentException(
                    "Customer type must be COMPANY or PERSON"
            );
        }

        BigDecimal discount = request.discountPercent() == null
                ? BigDecimal.ZERO
                : request.discountPercent();

        validateDiscount(discount);
        return discount;
    }

    private void validateDiscount(BigDecimal discount) {
        if (discount == null
                || discount.signum() < 0
                || discount.compareTo(new BigDecimal("100")) > 0
                || discount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Discount must be between 0 and 100 "
                            + "with at most two decimal places"
            );
        }
    }
}