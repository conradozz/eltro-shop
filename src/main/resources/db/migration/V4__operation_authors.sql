ALTER TABLE sales
    ADD COLUMN created_by_user_id BIGINT NULL,
    ADD CONSTRAINT fk_sales_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES users (id);

ALTER TABLE stock_movements
    ADD COLUMN created_by_user_id BIGINT NULL,
    ADD CONSTRAINT fk_stock_movement_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES users (id);

ALTER TABLE sale_revisions
    ADD COLUMN corrected_by_user_id BIGINT NULL,
    ADD CONSTRAINT fk_sale_revision_corrected_by_user
        FOREIGN KEY (corrected_by_user_id)
        REFERENCES users (id);