package pl.com.eltro.assortment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserSecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserAccountService userAccountService;

    @Test
    void anonymousUserCannotReadAccounts() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(userRepository);
    }

    @Test
    @WithMockUser(username = "sprzedawca", roles = "SELLER")
    void sellerCannotReadAccounts() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    @Test
    @WithMockUser(username = "sprzedawca", roles = "SELLER")
    void sellerCannotCreateAccountEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/api/users")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "another-user",
                                    "password": "Test-password-123!",
                                    "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "sprzedawca", roles = "SELLER")
    void sellerCannotDisableAccount() throws Exception {
        mvc.perform(patch("/api/users/1/enabled")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAccountService);
    }

    @Test
    @WithMockUser(username = "sprzedawca", roles = "SELLER")
    void sellerCannotResetPassword() throws Exception {
        mvc.perform(patch("/api/users/1/password")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "Changed-password-123!"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAccountService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanReadAccounts() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(
                new UserSummary(1L, "admin", "ADMIN", true),
                new UserSummary(2L, "sprzedawca", "SELLER", true)
        ));

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[1].role").value("SELLER"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        verify(userRepository).findAll();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateAccountWithValidCsrf() throws Exception {
        when(userService.create(any(CreateUserRequest.class)))
                .thenReturn(
                        new UserSummary(3L, "new-seller", "SELLER", true)
                );

        mvc.perform(post("/api/users")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "new-seller",
                                    "password": "Test-password-123!",
                                    "role": "SELLER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-seller"))
                .andExpect(jsonPath("$.role").value("SELLER"));

        verify(userService).create(
                new CreateUserRequest(
                        "new-seller",
                        "Test-password-123!",
                        "SELLER"
                )
        );
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotCreateAccountWithoutCsrf() throws Exception {
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "new-seller",
                                    "password": "Test-password-123!",
                                    "role": "SELLER"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanResetPasswordWithValidCsrf() throws Exception {
        mvc.perform(patch("/api/users/2/password")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "Changed-password-123!"}
                                """))
                .andExpect(status().isNoContent());

        verify(userAccountService).updatePassword(
                eq(2L),
                eq(new UpdateUserPasswordRequest(
                        "Changed-password-123!"
                ))
        );
    }
}