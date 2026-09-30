package pl.com.eltro.assortment;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;

    public UserAccountService(
            UserRepository userRepository,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder,
            SessionRegistry sessionRegistry
    ) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional
    public UserSummary updateEnabled(
            long userId,
            UpdateUserEnabledRequest request
    ) {
        if (request == null || request.enabled() == null) {
            throw new IllegalArgumentException("Enabled is required");
        }

        long currentUserId = currentUserService.requireUserId();

        if (!request.enabled() && userId == currentUserId) {
            throw new IllegalArgumentException(
                    "You cannot disable your own account"
            );
        }

        UserSummary user = requireUser(userId);

        userRepository.updateEnabled(userId, request.enabled());

        if (!request.enabled()) {
            expireSessionsAfterCommit(user.username());
        }

        return new UserSummary(
                user.id(),
                user.username(),
                user.role(),
                request.enabled()
        );
    }

    @Transactional
    public void updatePassword(
            long userId,
            UpdateUserPasswordRequest request
    ) {
        currentUserService.requireUserId();

        UserSummary user = requireUser(userId);

        String password = request == null ? null : request.password();

        if (password == null
                || password.isBlank()
                || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must contain at least 12 characters "
                            + "and at most 72 UTF-8 bytes"
            );
        }

        String passwordHash = passwordEncoder.encode(password);

        userRepository.updatePassword(userId, passwordHash);

        expireSessionsAfterCommit(user.username());
    }

    private UserSummary requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User does not exist"
                ));
    }

    private void expireSessionsAfterCommit(String username) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        expireSessions(username);
                    }
                }
        );
    }

    private void expireSessions(String username) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails userDetails
                    && userDetails.getUsername().equals(username)) {

                for (SessionInformation session :
                        sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                }
            }
        }
    }
}