package pl.com.eltro.assortment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
public class LoginController {

    private final String loginTemplate;
    private final boolean demoEnabled;

    public LoginController(
            @Value("${app.demo.enabled:false}") boolean demoEnabled
    ) throws IOException {
        this.demoEnabled = demoEnabled;

        ClassPathResource resource = new ClassPathResource(
                "templates/login.html"
        );

        try (InputStream input = resource.getInputStream()) {
            this.loginTemplate = StreamUtils.copyToString(
                    input,
                    StandardCharsets.UTF_8
            );
        }
    }

    @GetMapping(
            value = "/login",
            produces = "text/html;charset=UTF-8"
    )
    public ResponseEntity<String> login(
            CsrfToken csrfToken,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String demoError
    ) {
        String message = "";

        if (demoError != null) {
            message =
                    "Logowanie demo jest niedostępne. "
                            + "Konto może być wyłączone lub tryb demo nieaktywny.";
        } else if (error != null) {
            message =
                    "Nieprawidłowy login lub hasło albo konto jest nieaktywne.";
        } else if (expired != null) {
            message =
                    "Formularz lub sesja wygasły. Spróbuj ponownie.";
        } else if (logout != null) {
            message = "Zostałeś wylogowany.";
        }

        String html = loginTemplate
                .replace(
                        "__CSRF_PARAMETER__",
                        HtmlUtils.htmlEscape(
                                csrfToken.getParameterName()
                        )
                )
                .replace(
                        "__CSRF_TOKEN__",
                        HtmlUtils.htmlEscape(
                                csrfToken.getToken()
                        )
                )
                .replace(
                        "__MESSAGE_HIDDEN__",
                        message.isEmpty() ? "hidden" : ""
                )
                .replace(
                        "__MESSAGE__",
                        HtmlUtils.htmlEscape(message)
                )
                .replace(
                        "__DEMO_HIDDEN__",
                        demoEnabled ? "" : "hidden"
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(html);
    }
}