package pl.com.eltro.assortment;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class UserPermissionService {

    private final UserRepository userRepository;
    private final UserPermissionRepository permissionRepository;
    private final CurrentUserService currentUserService;
    private final SessionRegistry sessionRegistry;

    public UserPermissionService(
            UserRepository userRepository,
            UserPermissionRepository permissionRepository,
            CurrentUserService currentUserService,
            SessionRegistry sessionRegistry
    ) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.currentUserService = currentUserService;
        this.sessionRegistry = sessionRegistry;
    }

    public List<PermissionDefinition> catalog() {
        requireAdmin();

        return Arrays.stream(UserPermission.values())
                .map(permission -> new PermissionDefinition(
                        permission.name(),
                        permission.group(),
                        permission.label()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserPermissions get(long userId) {
        requireAdmin();

        UserSummary user = requireUser(userId);
        return describe(user);
    }

    @Transactional
    public UserPermissions update(
            long userId,
            UpdateUserPermissionsRequest request
    ) {
        requireAdmin();

        if (request == null || request.permissions() == null) {
            throw new IllegalArgumentException(
                    "Permissions are required"
            );
        }

        Set<UserPermission> permissions =
                EnumSet.noneOf(UserPermission.class);

        for (String code : request.permissions()) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException(
                        "Permission code cannot be empty"
                );
            }

            try {
                permissions.add(UserPermission.valueOf(code));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unknown permission: " + code
                );
            }
        }

        if (!permissionRepository.lockUser(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User does not exist"
            );
        }

        UserSummary user = requireUser(userId);

        if ("ADMIN".equals(user.role())) {
            throw new IllegalArgumentException(
                    "Administrator always has all permissions"
            );
        }

        Set<String> previous = Set.copyOf(
                permissionRepository.findByUserId(userId)
        );

        Set<String> requested = permissions.stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());

        if (!previous.equals(requested)) {
            permissionRepository.replace(userId, permissions);
            expireSessionsAfterCommit(user.username());
        }

        return describe(user);
    }

    private UserPermissions describe(UserSummary user) {
        boolean admin = "ADMIN".equals(user.role());

        List<String> permissions = admin
                ? Arrays.stream(UserPermission.values())
                  .map(Enum::name)
                  .toList()
                : permissionRepository.findByUserId(user.id());

        return new UserPermissions(
                user.id(),
                user.username(),
                user.role(),
                admin,
                permissions
        );
    }

    private UserSummary requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User does not exist"
                ));
    }

    private void requireAdmin() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        boolean admin = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                );

        if (!admin) {
            throw new AccessDeniedException(
                    "Administrator permission is required"
            );
        }

        currentUserService.requireUserId();
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
                    && username.equals(userDetails.getUsername())) {

                for (SessionInformation session :
                        sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                }
            }
        }
    }

    public record PermissionDefinition(
            String code,
            String group,
            String label
    ) {
    }

    public record UserPermissions(
            long userId,
            String username,
            String role,
            boolean administrator,
            List<String> permissions
    ) {
    }
}