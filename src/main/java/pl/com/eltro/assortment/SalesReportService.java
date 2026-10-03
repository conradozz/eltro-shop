package pl.com.eltro.assortment;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
public class SalesReportService {

    private static final ZoneId SHOP_ZONE =
            ZoneId.of("Europe/Warsaw");

    private final SalesReportRepository repository;
    private final CurrentUserService currentUserService;

    public SalesReportService(
            SalesReportRepository repository,
            CurrentUserService currentUserService
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    public SalesReport report(
            LocalDate from,
            LocalDate to,
            Long requestedUserId,
            String grouping
    ) {
        Long userId = resolveUser(requestedUserId);

        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Provide a valid date range"
            );
        }

        if (ChronoUnit.DAYS.between(from, to) > 3660) {
            throw new IllegalArgumentException(
                    "Date range cannot exceed 10 years"
            );
        }

        if (grouping == null
                || !List.of("DAY", "MONTH", "YEAR").contains(grouping)) {
            throw new IllegalArgumentException(
                    "Grouping must be DAY, MONTH or YEAR"
            );
        }

        List<SalesReport.Sale> sales =
                repository.findSales(from, to, userId);

        Map<String, List<SalesReport.Sale>> byPeriod =
                new TreeMap<>();

        Map<Long, List<SalesReport.Sale>> byEmployee =
                new LinkedHashMap<>();

        for (SalesReport.Sale sale : sales) {
            LocalDate date = sale.soldAt()
                    .atZoneSameInstant(SHOP_ZONE)
                    .toLocalDate();

            String period = switch (grouping) {
                case "MONTH" -> date.toString().substring(0, 7);
                case "YEAR" -> Integer.toString(date.getYear());
                default -> date.toString();
            };

            byPeriod.computeIfAbsent(
                    period,
                    key -> new ArrayList<>()
            ).add(sale);

            byEmployee.computeIfAbsent(
                    sale.userId(),
                    key -> new ArrayList<>()
            ).add(sale);
        }

        List<SalesReport.Period> periods = byPeriod.entrySet()
                .stream()
                .map(entry -> new SalesReport.Period(
                        entry.getKey(),
                        totals(entry.getValue())
                ))
                .toList();

        List<SalesReport.Employee> employees =
                byEmployee.entrySet()
                        .stream()
                        .map(entry -> new SalesReport.Employee(
                                entry.getKey(),
                                entry.getValue().get(0).username(),
                                totals(entry.getValue())
                        ))
                        .toList();

        return new SalesReport(
                from,
                to,
                grouping,
                userId,
                totals(sales),
                periods,
                employees,
                sales
        );
    }

    public SalesReport.Dashboard dashboard(Long requestedUserId) {
        Long userId = resolveUser(requestedUserId);
        LocalDate today = LocalDate.now(SHOP_ZONE);

        List<SalesReport.Sale> monthSales = repository.findSales(
                today.withDayOfMonth(1),
                today,
                userId
        );

        List<SalesReport.Sale> todaySales = monthSales.stream()
                .filter(sale -> sale.soldAt()
                        .atZoneSameInstant(SHOP_ZONE)
                        .toLocalDate()
                        .equals(today))
                .toList();

        return new SalesReport.Dashboard(
                today,
                userId,
                totals(todaySales),
                totals(monthSales)
        );
    }

    public List<SalesReport.User> users() {
        currentUserService.requireUserId();

        if (!isAdmin()) {
            throw new AccessDeniedException(
                    "Only administrators can list report users"
            );
        }

        return repository.findUsers();
    }

    private Long resolveUser(Long requestedUserId) {
        long currentId = currentUserService.requireUserId();

        if (!isAdmin()) {
            if (requestedUserId != null
                    && requestedUserId != currentId) {
                throw new AccessDeniedException(
                        "You can only view your own sales"
                );
            }

            return currentId;
        }

        if (requestedUserId != null
                && !repository.userExists(requestedUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User does not exist"
            );
        }

        return requestedUserId;
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );
    }

    private SalesReport.Totals totals(List<SalesReport.Sale> sales) {
        long quantity = sales.stream()
                .mapToLong(SalesReport.Sale::quantity)
                .sum();

        BigDecimal net = sales.stream()
                .map(SalesReport.Sale::totalNet)
                .reduce(
                        new BigDecimal("0.00"),
                        BigDecimal::add
                );

        BigDecimal gross = sales.stream()
                .map(SalesReport.Sale::totalGross)
                .reduce(
                        new BigDecimal("0.00"),
                        BigDecimal::add
                );

        return new SalesReport.Totals(
                sales.size(),
                quantity,
                net,
                gross
        );
    }
}