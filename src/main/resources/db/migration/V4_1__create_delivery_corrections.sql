CREATE TABLE IF NOT EXISTS delivery_corrections (
                                                    id BIGINT NOT NULL AUTO_INCREMENT,
                                                    delivery_movement_id BIGINT NOT NULL,

                                                    previous_quantity INT NOT NULL,
                                                    new_quantity INT NOT NULL,

                                                    previous_purchase_price_net DECIMAL(12,2) NULL,
    new_purchase_price_net DECIMAL(12,2) NULL,

    previous_markup_percent DECIMAL(7,2) NULL,
    new_markup_percent DECIMAL(7,2) NULL,

    previous_sale_price_net DECIMAL(12,2) NULL,
    new_sale_price_net DECIMAL(12,2) NULL,

    previous_remarks TEXT NULL,
    new_remarks TEXT NULL,

    corrected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_delivery_correction_movement
    FOREIGN KEY (delivery_movement_id)
    REFERENCES stock_movements (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;