package pl.com.eltro.assortment;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports/sales")
public class SalesReportController {

    private final SalesReportService service;

    public SalesReportController(SalesReportService service) {
        this.service = service;
    }

    @GetMapping
    public SalesReport report(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false)
            Long userId,

            @RequestParam(defaultValue = "DAY")
            String grouping
    ) {
        return service.report(from, to, userId, grouping);
    }

    @GetMapping("/dashboard")
    public SalesReport.Dashboard dashboard(
            @RequestParam(required = false) Long userId
    ) {
        return service.dashboard(userId);
    }

    @GetMapping("/users")
    public List<SalesReport.User> users() {
        return service.users();
    }
}