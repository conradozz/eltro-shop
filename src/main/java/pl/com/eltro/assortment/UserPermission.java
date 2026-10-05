package pl.com.eltro.assortment;

public enum UserPermission {

    PRODUCT_CREATE("Produkty", "Dodawanie produktów"),
    PRODUCT_EDIT("Produkty", "Edycja danych produktów"),
    PRODUCT_PRICE_EDIT("Produkty", "Zmiana cen produktów"),

    CUSTOMER_CREATE("Klienci", "Dodawanie klientów"),
    CUSTOMER_EDIT("Klienci", "Edycja danych klientów"),
    CUSTOMER_DISCOUNT_EDIT("Klienci", "Zmiana rabatów"),

    DELIVERY_CREATE("Dostawy", "Przyjmowanie dostaw"),
    DELIVERY_EDIT("Dostawy", "Korekty dostaw"),

    SALE_CREATE("Sprzedaż", "Tworzenie sprzedaży"),
    SALE_EDIT("Sprzedaż", "Korekty sprzedaży"),

    ORDER_MANAGE("Zamówienia", "Obsługa zamówień"),
    DOCUMENT_CREATE("Dokumenty", "Wystawianie dokumentów"),

    REPORT_ALL("Raporty", "Raporty wszystkich pracowników");

    private final String group;
    private final String label;

    UserPermission(String group, String label) {
        this.group = group;
        this.label = label;
    }

    public String group() {
        return group;
    }

    public String label() {
        return label;
    }
}