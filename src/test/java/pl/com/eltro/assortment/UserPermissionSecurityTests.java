package pl.com.eltro.assortment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserPermissionSecurityTests.ProbeController.class
)
@Import({
        SecurityConfig.class,
        UserPermissionSecurityTests.ProbeController.class
})
class UserPermissionSecurityTests {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @CsvSource({
            "POST, /api/products, PRODUCT_CREATE",
            "PATCH, /api/products/1/details, PRODUCT_EDIT",
            "PATCH, /api/products/1/prices, PRODUCT_PRICE_EDIT",
            "POST, /api/customers, CUSTOMER_CREATE",
            "PUT, /api/customers/1, CUSTOMER_EDIT",
            "PATCH, /api/customers/1/discount, CUSTOMER_DISCOUNT_EDIT",
            "POST, /api/products/1/deliveries, DELIVERY_CREATE",
            "PUT, /api/deliveries/1, DELIVERY_EDIT",
            "POST, /api/sales, SALE_CREATE",
            "PUT, /api/sales/1, SALE_EDIT",
            "POST, /api/purchase-orders, ORDER_MANAGE",
            "PUT, /api/purchase-orders/1, ORDER_MANAGE",
            "PATCH, /api/purchase-orders/1/ordered, ORDER_MANAGE",
            "PATCH, /api/purchase-orders/1/cancel, ORDER_MANAGE",
            "POST, /api/sales/1/documents, DOCUMENT_CREATE",
            "GET, /api/reports/sales/users, REPORT_ALL"
    })
    void sellerWithMatchingPermissionCanAccessEndpoint(
            String method,
            String path,
            String permission
    ) throws Exception {
        mvc.perform(call(method, path)
                        .with(user("sprzedawca").authorities(
                                new SimpleGrantedAuthority("ROLE_SELLER"),
                                new SimpleGrantedAuthority(permission)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handled").value(true));
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/products",
            "PATCH, /api/products/1/details",
            "PATCH, /api/products/1/prices",
            "POST, /api/customers",
            "PUT, /api/customers/1",
            "PATCH, /api/customers/1/discount",
            "POST, /api/products/1/deliveries",
            "PUT, /api/deliveries/1",
            "POST, /api/sales",
            "PUT, /api/sales/1",
            "POST, /api/purchase-orders",
            "PUT, /api/purchase-orders/1",
            "PATCH, /api/purchase-orders/1/ordered",
            "PATCH, /api/purchase-orders/1/cancel",
            "POST, /api/sales/1/documents",
            "GET, /api/reports/sales/users"
    })
    void sellerWithoutPermissionIsForbidden(
            String method,
            String path
    ) throws Exception {
        mvc.perform(call(method, path)
                        .with(user("sprzedawca").roles("SELLER")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/products",
            "PATCH, /api/products/1/details",
            "PATCH, /api/products/1/prices",
            "POST, /api/customers",
            "PUT, /api/customers/1",
            "PATCH, /api/customers/1/discount",
            "POST, /api/products/1/deliveries",
            "PUT, /api/deliveries/1",
            "POST, /api/sales",
            "PUT, /api/sales/1",
            "POST, /api/purchase-orders",
            "PUT, /api/purchase-orders/1",
            "PATCH, /api/purchase-orders/1/ordered",
            "PATCH, /api/purchase-orders/1/cancel",
            "POST, /api/sales/1/documents",
            "GET, /api/reports/sales/users"
    })
    void adminCanAccessWithoutIndividualPermissions(
            String method,
            String path
    ) throws Exception {
        mvc.perform(call(method, path)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handled").value(true));
    }

    @Test
    void unrelatedPermissionDoesNotAllowProductCreation()
            throws Exception {
        mvc.perform(call("POST", "/api/products")
                        .with(user("sprzedawca").authorities(
                                new SimpleGrantedAuthority("ROLE_SELLER"),
                                new SimpleGrantedAuthority("CUSTOMER_CREATE")
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void productPermissionDoesNotBypassCsrf() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/products")
                        .with(user("sprzedawca").authorities(
                                new SimpleGrantedAuthority("ROLE_SELLER"),
                                new SimpleGrantedAuthority("PRODUCT_CREATE")
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerCannotGrantPermissionsToOwnAccount()
            throws Exception {
        mvc.perform(call("PUT", "/api/users/2/permissions")
                        .with(user("sprzedawca").authorities(
                                new SimpleGrantedAuthority("ROLE_SELLER"),
                                new SimpleGrantedAuthority("PRODUCT_CREATE"),
                                new SimpleGrantedAuthority("REPORT_ALL")
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessPermissionManagement() throws Exception {
        mvc.perform(call("PUT", "/api/users/2/permissions")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handled").value(true));
    }

    @Test
    void sellerCanReadProductsWithoutWritePermissions()
            throws Exception {
        mvc.perform(call("GET", "/api/products")
                        .with(user("sprzedawca").roles("SELLER")))
                .andExpect(status().isOk());
    }

    @Test
    void sellerCanReadDocumentsWithoutCreationPermission()
            throws Exception {
        mvc.perform(call("GET", "/api/documents/1")
                        .with(user("sprzedawca").roles("SELLER")))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder call(
            String method,
            String path
    ) {
        return request(HttpMethod.valueOf(method), path)
                .with(csrf().asHeader());
    }

    @RestController
    static class ProbeController {

        @RequestMapping(
                path = {
                        "/api/products",
                        "/api/products/1/details",
                        "/api/products/1/prices",
                        "/api/customers",
                        "/api/customers/1",
                        "/api/customers/1/discount",
                        "/api/products/1/deliveries",
                        "/api/deliveries/1",
                        "/api/sales",
                        "/api/sales/1",
                        "/api/purchase-orders",
                        "/api/purchase-orders/1",
                        "/api/purchase-orders/1/ordered",
                        "/api/purchase-orders/1/cancel",
                        "/api/sales/1/documents",
                        "/api/documents/1",
                        "/api/reports/sales/users",
                        "/api/users/2/permissions"
                },
                method = {
                        RequestMethod.GET,
                        RequestMethod.POST,
                        RequestMethod.PUT,
                        RequestMethod.PATCH
                }
        )
        public Map<String, Boolean> handle() {
            return Map.of("handled", true);
        }
    }
}