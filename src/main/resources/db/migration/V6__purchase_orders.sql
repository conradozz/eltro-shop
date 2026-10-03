CREATE TABLE purchase_orders (
                                 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                 product_id BIGINT NOT NULL,
                                 product_name VARCHAR(200) NOT NULL,
                                 sku VARCHAR(80) NOT NULL,
                                 manufacturer VARCHAR(100),
                                 quantity INT NOT NULL,
                                 received_quantity INT NOT NULL DEFAULT 0,
                                 status VARCHAR(30) NOT NULL DEFAULT 'TO_ORDER',
                                 remarks TEXT,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 ordered_at DATETIME,
                                 created_by_user_id BIGINT NOT NULL,

                                 CONSTRAINT fk_purchase_order_product
                                     FOREIGN KEY (product_id) REFERENCES products(id),

                                 CONSTRAINT fk_purchase_order_author
                                     FOREIGN KEY (created_by_user_id) REFERENCES users(id),

                                 CONSTRAINT chk_purchase_order_quantity
                                     CHECK (
                                         quantity > 0
                                             AND received_quantity >= 0
                                             AND received_quantity <= quantity
                                         ),

                                 CONSTRAINT chk_purchase_order_status
                                     CHECK (
                                         status IN (
                                                    'TO_ORDER',
                                                    'ORDERED',
                                                    'PARTIALLY_RECEIVED',
                                                    'RECEIVED',
                                                    'CANCELLED'
                                             )
                                         ),

                                 INDEX ix_purchase_order_status_manufacturer (status, manufacturer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_order_events (
                                       id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                       purchase_order_id BIGINT NOT NULL,
                                       event_type VARCHAR(30) NOT NULL,
                                       previous_quantity INT,
                                       new_quantity INT NOT NULL,
                                       previous_status VARCHAR(30),
                                       new_status VARCHAR(30) NOT NULL,
                                       remarks TEXT,
                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       created_by_user_id BIGINT NOT NULL,

                                       CONSTRAINT fk_purchase_event_order
                                           FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),

                                       CONSTRAINT fk_purchase_event_author
                                           FOREIGN KEY (created_by_user_id) REFERENCES users(id),

                                       INDEX ix_purchase_event_order (purchase_order_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;