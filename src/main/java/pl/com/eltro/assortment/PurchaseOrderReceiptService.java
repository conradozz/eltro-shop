package pl.com.eltro.assortment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PurchaseOrderReceiptService {

    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUserService;

    public PurchaseOrderReceiptService(
            JdbcTemplate jdbc,
            CurrentUserService currentUserService
    ) {
        this.jdbc = jdbc;
        this.currentUserService = currentUserService;
    }

    public void lockProduct(long productId) {
        boolean exists = !jdbc.query(
                "SELECT id FROM products WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("id"),
                productId
        ).isEmpty();

        if (!exists) {
            throw new IllegalArgumentException("Product does not exist");
        }
    }

    public void validateStockChange(long productId, long difference) {
        Integer current = jdbc.queryForObject(
                "SELECT quantity FROM products WHERE id = ?",
                Integer.class,
                productId
        );

        long result = Objects.requireNonNull(current) + difference;

        if (result < 0 || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Cannot change delivery: insufficient stock or stock is too large"
            );
        }
    }

    public void allocate(long productId, long deliveryId, int quantity) {
        if (quantity <= 0) {
            return;
        }

        List<OrderRow> orders = jdbc.query(
                """
                SELECT id, quantity, received_quantity, status
                FROM purchase_orders
                WHERE product_id = ?
                  AND status IN ('ORDERED', 'PARTIALLY_RECEIVED')
                  AND received_quantity < quantity
                ORDER BY ordered_at, id
                FOR UPDATE
                """,
                (rs, rowNum) -> new OrderRow(
                        rs.getLong("id"),
                        rs.getInt("quantity"),
                        rs.getInt("received_quantity"),
                        rs.getString("status")
                ),
                productId
        );

        int remaining = quantity;

        for (OrderRow order : orders) {
            if (remaining == 0) {
                break;
            }

            int accepted = Math.min(
                    remaining,
                    order.quantity() - order.received()
            );

            jdbc.update(
                    """
                    INSERT INTO purchase_order_deliveries (
                        delivery_movement_id,
                        purchase_order_id,
                        allocated_quantity
                    )
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        allocated_quantity =
                            allocated_quantity + VALUES(allocated_quantity)
                    """,
                    deliveryId,
                    order.id(),
                    accepted
            );

            updateOrder(
                    order,
                    order.received() + accepted,
                    deliveryId,
                    "DELIVERY_RECEIVED"
            );

            remaining -= accepted;
        }
    }

    public void correct(
            long productId,
            long deliveryId,
            int previousQuantity,
            int newQuantity
    ) {
        int difference = newQuantity - previousQuantity;

        if (difference > 0) {
            allocate(productId, deliveryId, difference);
            return;
        }

        if (difference == 0) {
            return;
        }

        List<AllocationRow> allocations = jdbc.query(
                """
                SELECT a.id AS allocation_id,
                       a.allocated_quantity,
                       po.id,
                       po.quantity,
                       po.received_quantity,
                       po.status
                FROM purchase_order_deliveries a
                JOIN purchase_orders po
                    ON po.id = a.purchase_order_id
                WHERE a.delivery_movement_id = ?
                  AND po.product_id = ?
                ORDER BY po.ordered_at DESC, po.id DESC
                FOR UPDATE
                """,
                (rs, rowNum) -> new AllocationRow(
                        rs.getLong("allocation_id"),
                        rs.getInt("allocated_quantity"),
                        new OrderRow(
                                rs.getLong("id"),
                                rs.getInt("quantity"),
                                rs.getInt("received_quantity"),
                                rs.getString("status")
                        )
                ),
                deliveryId,
                productId
        );

        long assigned = allocations.stream()
                .mapToLong(AllocationRow::quantity)
                .sum();

        if (assigned > previousQuantity) {
            throw new IllegalStateException(
                    "Delivery allocations exceed delivery quantity"
            );
        }

        // Najpierw odejmujemy nadwyżkę nieprzypisaną do zamówień.
        long unallocated = previousQuantity - assigned;
        long toRemove = Math.max(
                0L,
                (long) previousQuantity - newQuantity - unallocated
        );

        for (AllocationRow allocation : allocations) {
            if (toRemove == 0) {
                break;
            }

            int removed = (int) Math.min(
                    toRemove,
                    allocation.quantity()
            );

            int left = allocation.quantity() - removed;

            if (left == 0) {
                jdbc.update(
                        "DELETE FROM purchase_order_deliveries WHERE id = ?",
                        allocation.id()
                );
            } else {
                jdbc.update(
                        """
                        UPDATE purchase_order_deliveries
                        SET allocated_quantity = ?
                        WHERE id = ?
                        """,
                        left,
                        allocation.id()
                );
            }

            OrderRow order = allocation.order();

            updateOrder(
                    order,
                    order.received() - removed,
                    deliveryId,
                    "DELIVERY_CORRECTED"
            );

            toRemove -= removed;
        }

        if (toRemove != 0) {
            throw new IllegalStateException(
                    "Cannot reconcile delivery correction"
            );
        }
    }

    private void updateOrder(
            OrderRow order,
            int received,
            long deliveryId,
            String eventType
    ) {
        if (received < 0 || received > order.quantity()) {
            throw new IllegalStateException("Invalid received quantity");
        }

        String status;

        if (received == 0) {
            status = "ORDERED";
        } else if (received == order.quantity()) {
            status = "RECEIVED";
        } else {
            status = "PARTIALLY_RECEIVED";
        }

        jdbc.update(
                """
                UPDATE purchase_orders
                SET received_quantity = ?, status = ?
                WHERE id = ?
                """,
                received,
                status,
                order.id()
        );

        jdbc.update(
                """
                INSERT INTO purchase_order_events (
                    purchase_order_id, event_type,
                    previous_quantity, new_quantity,
                    previous_status, new_status,
                    remarks, created_by_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.id(),
                eventType,
                order.quantity(),
                order.quantity(),
                order.status(),
                status,
                "Dostawa #" + deliveryId
                        + ": przyjęto " + order.received()
                        + " → " + received
                        + " z " + order.quantity() + " szt.",
                currentUserService.requireUserId()
        );
    }

    private record OrderRow(
            long id,
            int quantity,
            int received,
            String status
    ) {}

    private record AllocationRow(
            long id,
            int quantity,
            OrderRow order
    ) {}
}