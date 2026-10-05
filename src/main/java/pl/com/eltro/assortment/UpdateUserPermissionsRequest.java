package pl.com.eltro.assortment;

import java.util.List;

public record UpdateUserPermissionsRequest(
        List<String> permissions
) {
}