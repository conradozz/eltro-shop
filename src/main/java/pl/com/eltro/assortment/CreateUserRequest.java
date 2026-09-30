package pl.com.eltro.assortment;

public record CreateUserRequest(
        String username,
        String password,
        String role
) {
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username
                + ", password=***, role=" + role + "]";
    }
}