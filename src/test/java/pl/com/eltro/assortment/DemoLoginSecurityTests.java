package pl.com.eltro.assortment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                LoginController.class,
                CurrentUserController.class
        },
        properties = "app.demo.enabled=true"
)
@Import({
        SecurityConfig.class,
        DemoLoginSecurityConfig.class
})
class DemoLoginSecurityTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SessionRegistry sessionRegistry;

    @MockBean
    private UserDetailsService userDetailsService;

    private UserDetails demoUser;

    @BeforeEach
    void prepareDemoUser() {
        String[] authorities = Arrays.stream(UserPermission.values())
                .map(Enum::name)
                .toArray(String[]::new);

        String[] allAuthorities = new String[authorities.length + 1];
        allAuthorities[0] = "ROLE_SELLER";

        System.arraycopy(
                authorities,
                0,
                allAuthorities,
                1,
                authorities.length
        );

        demoUser = User.withUsername("demo")
                .password("unused")
                .authorities(allAuthorities)
                .build();

        when(userDetailsService.loadUserByUsername("demo"))
                .thenReturn(demoUser);
    }

    @Test
    void loginPageShowsDemoButton() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "Zaloguj jako użytkownik demo"
                        )
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "__CSRF_TOKEN__"
                                )
                        )
                ));
    }

    @Test
    void demoLoginCreatesAuthenticatedSession() throws Exception {
        var result = mvc.perform(
                        post("/login/demo")
                                .servletPath("/login/demo")
                                .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession session = (MockHttpSession)
                result.getRequest().getSession(false);

        assertThat(session).isNotNull();

        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.roles[0]").value("SELLER"))
                .andExpect(jsonPath("$.permissions.length()").value(13));

        assertThat(
                sessionRegistry.getAllSessions(demoUser, false)
        ).extracting(sessionInfo -> sessionInfo.getSessionId())
                .contains(session.getId());
    }

    @Test
    void requestCannotChooseAdministratorAccount() throws Exception {
        mvc.perform(
                        post("/login/demo")
                                .servletPath("/login/demo")
                                .with(csrf())
                                .param("username", "admin")
                                .param("password", "anything")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(userDetailsService).loadUserByUsername("demo");
    }

    @Test
    void demoLoginWithoutCsrfIsRejected() throws Exception {
        mvc.perform(
                        post("/login/demo")
                                .servletPath("/login/demo")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verifyNoInteractions(userDetailsService);
    }

    @Test
    void disabledDemoAccountCannotLogin() throws Exception {
        when(userDetailsService.loadUserByUsername("demo"))
                .thenReturn(
                        User.withUsername("demo")
                                .password("unused")
                                .roles("SELLER")
                                .disabled(true)
                                .build()
                );

        mvc.perform(
                        post("/login/demo")
                                .servletPath("/login/demo")
                                .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?demoError"));
    }

    @Test
    void administratorRoleCannotBeUsedForDemoLogin() throws Exception {
        when(userDetailsService.loadUserByUsername("demo"))
                .thenReturn(
                        User.withUsername("demo")
                                .password("unused")
                                .roles("ADMIN")
                                .build()
                );

        mvc.perform(
                        post("/login/demo")
                                .servletPath("/login/demo")
                                .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?demoError"));
    }

    @Test
    void missingDemoAccountReturnsLoginError() throws Exception {
        when(userDetailsService.loadUserByUsername("demo"))
                .thenThrow(new UsernameNotFoundException("Missing"));

        mvc.perform(
                        post("/login/demo")
                                .servletPath("/login/demo")
                                .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?demoError"));
    }

    @Test
    void demoCannotReadUserAccounts() throws Exception {
        MockHttpSession session = loginDemo();

        mvc.perform(get("/api/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoCannotChangeUserPermissions() throws Exception {
        MockHttpSession session = loginDemo();

        mvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .put("/api/users/1/permissions")
                                .session(session)
                                .with(csrf())
                                .contentType("application/json")
                                .content("""
                                        {"permissions":["REPORT_ALL"]}
                                        """)
                )
                .andExpect(status().isForbidden());
    }

    private MockHttpSession loginDemo() throws Exception {
        var result = mvc.perform(
                        post("/login/demo")
                                .servletPath("/login/demo")
                                .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        return (MockHttpSession)
                result.getRequest().getSession(false);
    }
}