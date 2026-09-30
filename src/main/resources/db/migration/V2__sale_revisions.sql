CREATE TABLE IF NOT EXISTS sale_revisions (
                                              id BIGINT NOT NULL AUTO_INCREMENT,
                                              sale_id BIGINT NOT NULL,
                                              previous_snapshot JSON NOT NULL,
                                              new_snapshot JSON NOT NULL,
                                              corrected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              PRIMARY KEY (id),
    KEY fk_sale_revision_sale (sale_id),
    CONSTRAINT fk_sale_revision_sale
    FOREIGN KEY (sale_id) REFERENCES sales (id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;