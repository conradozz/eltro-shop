package pl.com.eltro.assortment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository orders;
    private final ProductRepository products;
    private final CurrentUserService currentUser;

    public PurchaseOrderService(
            PurchaseOrderRepository orders,
            ProductRepository products,
            CurrentUserService currentUser
    ) {
        this.orders = orders;
        this.products = products;
        this.currentUser = currentUser;
    }

    public List<PurchaseOrder> findAll() {
        return orders.findAll();
    }

    public PurchaseOrder get(long id) {
        return orders.findById(id).orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Zamówienie nie istnieje"
                )
        );
    }

    @Transactional
    public PurchaseOrder create(PurchaseOrderRequest request) {
        validate(request);

        long author = currentUser.requireUserId();

        Product product = products.findById(request.productId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Produkt nie istnieje"
                        )
                );

        long id = orders.create(product, request, author);
        PurchaseOrder result = get(id);

        orders.addEvent(
                id,
                "CREATED",
                null,
                result,
                author
        );

        return result;
    }

    @Transactional
    public PurchaseOrder update(
            long id,
            PurchaseOrderRequest request
    ) {
        validate(request);

        long author = currentUser.requireUserId();
        PurchaseOrder previous = lockAndGet(id);

        if (!"TO_ORDER".equals(previous.status())) {
            throw new IllegalArgumentException(
                    "Edytować można tylko pozycję do zamówienia"
            );
        }

        if (request.productId().longValue() != previous.productId()) {
            throw new IllegalArgumentException(
                    "Nie można zmienić produktu zamówienia"
            );
        }

        orders.updateDraft(id, request);
        PurchaseOrder result = get(id);

        orders.addEvent(
                id,
                "UPDATED",
                previous,
                result,
                author
        );

        return result;
    }

    @Transactional
    public PurchaseOrder markOrdered(long id) {
        return transition(id, "ORDERED");
    }

    @Transactional
    public PurchaseOrder cancel(long id) {
        return transition(id, "CANCELLED");
    }

    public List<PurchaseOrderEvent> history(long id) {
        get(id);
        return orders.findEvents(id);
    }

    private PurchaseOrder transition(long id, String status) {
        long author = currentUser.requireUserId();
        PurchaseOrder previous = lockAndGet(id);

        if (status.equals(previous.status())) {
            return previous;
        }

        boolean allowed =
                "TO_ORDER".equals(previous.status())
                        || (
                        "CANCELLED".equals(status)
                                && "ORDERED".equals(previous.status())
                );

        if (!allowed || previous.receivedQuantity() != 0) {
            throw new IllegalArgumentException(
                    "Nie można wykonać tej zmiany statusu"
            );
        }

        orders.changeStatus(id, status);
        PurchaseOrder result = get(id);

        orders.addEvent(
                id,
                status,
                previous,
                result,
                author
        );

        return result;
    }

    private PurchaseOrder lockAndGet(long id) {
        if (!orders.lock(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Zamówienie nie istnieje"
            );
        }

        return get(id);
    }

    private void validate(PurchaseOrderRequest request) {
        if (request == null
                || request.productId() == null
                || request.productId() <= 0) {
            throw new IllegalArgumentException(
                    "Wybierz produkt"
            );
        }

        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "Liczba sztuk musi być dodatnią liczbą całkowitą"
            );
        }

        if (request.remarks() != null
                && request.remarks().length() > 2000) {
            throw new IllegalArgumentException(
                    "Uwagi mogą mieć maksymalnie 2000 znaków"
            );
        }
    }
}