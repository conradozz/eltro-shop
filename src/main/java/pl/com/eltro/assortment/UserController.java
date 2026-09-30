package pl.com.eltro.assortment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserAccountService userAccountService;

    public UserController(
            UserService userService,
            UserRepository userRepository,
            UserAccountService userAccountService
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public List<UserSummary> getAll() {
        return userRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary create(@RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}/enabled")
    public UserSummary updateEnabled(
            @PathVariable long id,
            @RequestBody UpdateUserEnabledRequest request
    ) {
        return userAccountService.updateEnabled(id, request);
    }

    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(
            @PathVariable long id,
            @RequestBody UpdateUserPasswordRequest request
    ) {
        userAccountService.updatePassword(id, request);
    }
}