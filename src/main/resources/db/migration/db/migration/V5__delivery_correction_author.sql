ALTER TABLE delivery_corrections
    ADD COLUMN corrected_by_user_id BIGINT NULL,
    ADD CONSTRAINT fk_delivery_correction_author
        FOREIGN KEY (corrected_by_user_id)
        REFERENCES users (id);