CREATE TABLE purchase_order_deliveries (
                                           id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                           delivery_movement_id BIGINT NOT NULL,
                                           purchase_order_id BIGINT NOT NULL,
                                           allocated_quantity INT NOT NULL,

                                           CONSTRAINT fk_order_delivery_movement
                                               FOREIGN KEY (delivery_movement_id)
                                                   REFERENCES stock_movements(id),

                                           CONSTRAINT fk_order_delivery_order
                                               FOREIGN KEY (purchase_order_id)
                                                   REFERENCES purchase_orders(id),

                                           CONSTRAINT uq_order_delivery
                                               UNIQUE (delivery_movement_id, purchase_order_id),

                                           CONSTRAINT chk_order_delivery_quantity
                                               CHECK (allocated_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_purchase_order_receipt
    ON purchase_orders (product_id, status, ordered_at, id);