CREATE TABLE document_issuer_settings (
                                          id TINYINT NOT NULL,
                                          company_name VARCHAR(200) DEFAULT NULL,
                                          nip VARCHAR(10) DEFAULT NULL,
                                          street VARCHAR(200) DEFAULT NULL,
                                          postal_code VARCHAR(10) DEFAULT NULL,
                                          city VARCHAR(100) DEFAULT NULL,
                                          email VARCHAR(200) DEFAULT NULL,
                                          phone VARCHAR(30) DEFAULT NULL,
                                          bank_account VARCHAR(34) DEFAULT NULL,

                                          PRIMARY KEY (id),

                                          CONSTRAINT chk_document_issuer_single_row
                                              CHECK (id = 1)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


INSERT INTO document_issuer_settings (id)
VALUES (1);


CREATE TABLE document_sequences (
                                    document_type VARCHAR(20) NOT NULL,
                                    document_year SMALLINT NOT NULL,
                                    last_number BIGINT NOT NULL DEFAULT 0,

                                    PRIMARY KEY (document_type, document_year),

                                    CONSTRAINT chk_document_sequence_type
                                        CHECK (
                                            document_type IN (
                                                              'INVOICE',
                                                              'SALE_CONFIRMATION'
                                                )
                                            ),

                                    CONSTRAINT chk_document_sequence_number
                                        CHECK (last_number >= 0),

                                    CONSTRAINT chk_document_sequence_year
                                        CHECK (document_year BETWEEN 2000 AND 9999)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE sales_documents (
                                 id BIGINT NOT NULL AUTO_INCREMENT,
                                 sale_id BIGINT NOT NULL,

                                 document_type VARCHAR(20) NOT NULL,
                                 document_number VARCHAR(60) NOT NULL,

                                 issued_on DATE NOT NULL,
                                 sale_on DATE NOT NULL,

                                 issuer_snapshot JSON NOT NULL,
                                 customer_snapshot JSON DEFAULT NULL,
                                 sale_snapshot JSON NOT NULL,

                                 total_net DECIMAL(14,2) NOT NULL,
                                 total_gross DECIMAL(14,2) NOT NULL,

                                 payment_status VARCHAR(20) NOT NULL,
                                 payment_method VARCHAR(20) NOT NULL,
                                 payment_due_on DATE DEFAULT NULL,

                                 goods_issued BOOLEAN NOT NULL DEFAULT FALSE,

                                 recipient_email VARCHAR(200) DEFAULT NULL,
                                 remarks TEXT,

                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 created_by_user_id BIGINT NOT NULL,

                                 PRIMARY KEY (id),

                                 UNIQUE KEY uq_sales_document_number (
                                     document_number
                                     ),

                                 UNIQUE KEY uq_sales_document_sale_type (
                                     sale_id,
                                     document_type
                                     ),

                                 KEY idx_sales_documents_issued_on (
        issued_on
    ),

                                 KEY idx_sales_documents_author (
        created_by_user_id
    ),

                                 CONSTRAINT fk_sales_document_sale
                                     FOREIGN KEY (sale_id)
                                         REFERENCES sales (id),

                                 CONSTRAINT fk_sales_document_author
                                     FOREIGN KEY (created_by_user_id)
                                         REFERENCES users (id),

                                 CONSTRAINT chk_sales_document_type
                                     CHECK (
                                         document_type IN (
                                                           'INVOICE',
                                                           'SALE_CONFIRMATION'
                                             )
                                         ),

                                 CONSTRAINT chk_sales_document_payment_status
                                     CHECK (
                                         payment_status IN (
                                                            'PAID',
                                                            'DEFERRED',
                                                            'UNPAID'
                                             )
                                         ),

                                 CONSTRAINT chk_sales_document_payment_method
                                     CHECK (
                                         payment_method IN (
                                                            'CASH',
                                                            'CARD',
                                                            'TRANSFER',
                                                            'OTHER'
                                             )
                                         ),

                                 CONSTRAINT chk_sales_document_payment_due
                                     CHECK (
                                         payment_status <> 'DEFERRED'
                                             OR payment_due_on IS NOT NULL
                                         ),

                                 CONSTRAINT chk_sales_document_totals
                                     CHECK (
                                         total_net >= 0
                                             AND total_gross >= total_net
                                         ),

                                 CONSTRAINT chk_sales_document_goods_issued
                                     CHECK (
                                         goods_issued IN (0, 1)
                                         )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE document_email_history (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        document_id BIGINT NOT NULL,
                                        recipient_email VARCHAR(200) NOT NULL,

                                        send_status VARCHAR(20) NOT NULL,
                                        error_message VARCHAR(1000) DEFAULT NULL,

                                        attempted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        sent_by_user_id BIGINT NOT NULL,

                                        PRIMARY KEY (id),

                                        KEY idx_document_email_history_document (
        document_id,
        attempted_at
    ),

                                        KEY idx_document_email_history_author (
        sent_by_user_id
    ),

                                        CONSTRAINT fk_document_email_history_document
                                            FOREIGN KEY (document_id)
                                                REFERENCES sales_documents (id),

                                        CONSTRAINT fk_document_email_history_author
                                            FOREIGN KEY (sent_by_user_id)
                                                REFERENCES users (id),

                                        CONSTRAINT chk_document_email_history_status
                                            CHECK (
                                                send_status IN (
                                                                'SENT',
                                                                'FAILED'
                                                    )
                                                )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;