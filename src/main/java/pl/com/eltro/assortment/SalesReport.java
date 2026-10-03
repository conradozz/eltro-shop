package pl.com.eltro.assortment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SalesReport(
        LocalDate from,
        LocalDate to,
        String grouping,
        Long userId,
        Totals totals,
        List<Period> periods,
        List<Employee> employees,
        List<Sale> sales
) {

    public record Totals(
            long saleCount,
            long quantity,
            BigDecimal totalNet,
            BigDecimal totalGross
    ) {}

    public record Period(
            String period,
            Totals totals
    ) {}

    public record Employee(
            Long userId,
            String username,
            Totals totals
    ) {}

    public record Sale(
            long id,
            OffsetDateTime soldAt,
            Long userId,
            String username,
            Long customerId,
            String remarks,
            long quantity,
            BigDecimal totalNet,
            BigDecimal totalGross
    ) {}

    public record User(
            long id,
            String username
    ) {}

    public record Dashboard(
            LocalDate date,
            Long userId,
            Totals today,
            Totals month
    ) {}
}