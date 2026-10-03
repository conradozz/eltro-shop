package pl.com.eltro.assortment;

import org.springframework.core.io.ClassPathResource;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
public class LoginController {

    private final String loginTemplate;

    public LoginController() throws IOException {
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
    @ResponseBody
    public String login(
            CsrfToken csrfToken,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String logout
    ) {
        String message = "";

        if (error != null) {
            message = "Nieprawidłowy login lub hasło albo konto jest nieaktywne.";
        } else if (expired != null) {
            message = "Sesja wygasła. Zaloguj się ponownie.";
        } else if (logout != null) {
            message = "Zostałeś wylogowany.";
        }

        return loginTemplate
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
                );
    }
}