package pl.com.eltro.assortment;

public record UpdateUserPasswordRequest(
        String password
) {
    @Override
    public String toString() {
        return "UpdateUserPasswordRequest[password=***]";
    }
}