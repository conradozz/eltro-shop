package pl.com.eltro.assortment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer create(CreateCustomerRequest request) {
        BigDecimal discount = validateAndGetDiscount(request);

        long id = customerRepository.create(request, discount);
        return customerRepository.findById(id).orElseThrow();
    }

    public Customer update(
            long customerId,
            CreateCustomerRequest request
    ) {
        BigDecimal discount = validateAndGetDiscount(request);

        int updatedRows = customerRepository.update(
                customerId,
                request,
                discount
        );

        if (updatedRows == 0) {
            throw new IllegalArgumentException(
                    "Customer does not exist"
            );
        }

        return customerRepository.findById(customerId).orElseThrow();
    }

    public Customer updateDiscount(
            long customerId,
            UpdateDiscountRequest request
    ) {
        BigDecimal discount = request.discountPercent();

        if (discount == null
                || discount.signum() < 0
                || discount.compareTo(new BigDecimal("100")) > 0
                || discount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Discount must be between 0 and 100 " +
                            "with at most two decimal places"
            );
        }

        if (customerRepository.updateDiscount(
                customerId,
                discount
        ) == 0) {
            throw new IllegalArgumentException(
                    "Customer does not exist"
            );
        }

        return customerRepository.findById(customerId).orElseThrow();
    }

    private BigDecimal validateAndGetDiscount(
            CreateCustomerRequest request
    ) {
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
                        "Private customer cannot have " +
                                "company name, NIP or REGON"
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

        if (discount.signum() < 0
                || discount.compareTo(new BigDecimal("100")) > 0
                || discount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Discount must be between 0 and 100 " +
                            "with at most two decimal places"
            );
        }

        return discount;
    }
}