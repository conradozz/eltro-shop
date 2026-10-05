package pl.com.eltro.assortment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserPermissionController {

    private final UserPermissionService service;

    public UserPermissionController(UserPermissionService service) {
        this.service = service;
    }

    @GetMapping("/permissions/catalog")
    public List<UserPermissionService.PermissionDefinition> catalog() {
        return service.catalog();
    }

    @GetMapping("/{id}/permissions")
    public UserPermissionService.UserPermissions get(
            @PathVariable long id
    ) {
        return service.get(id);
    }

    @PutMapping("/{id}/permissions")
    public UserPermissionService.UserPermissions update(
            @PathVariable long id,
            @RequestBody UpdateUserPermissionsRequest request
    ) {
        return service.update(id, request);
    }
}