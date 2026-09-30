CREATE TABLE products (
                          id BIGINT NOT NULL AUTO_INCREMENT,
                          sku VARCHAR(80) NOT NULL,
                          manufacturer_part_number VARCHAR(100),
                          ean VARCHAR(14),
                          name VARCHAR(200) NOT NULL,
                          manufacturer VARCHAR(100),
                          model VARCHAR(100),
                          category VARCHAR(100),
                          purchase_price_net DECIMAL(12, 2) NOT NULL,
                          sale_price_net DECIMAL(12, 2) NOT NULL,
                          vat_rate DECIMAL(5, 2) NOT NULL DEFAULT 23.00,
                          quantity INT NOT NULL DEFAULT 0,
                          minimum_quantity INT NOT NULL DEFAULT 0,
                          remarks TEXT,
                          PRIMARY KEY (id),
                          UNIQUE KEY uq_products_sku (sku),
                          CONSTRAINT chk_product_prices
                              CHECK (purchase_price_net >= 0 AND sale_price_net >= 0),
                          CONSTRAINT chk_product_quantity
                              CHECK (quantity >= 0 AND minimum_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customers (
                           id BIGINT NOT NULL AUTO_INCREMENT,
                           first_name VARCHAR(100),
                           last_name VARCHAR(100),
                           company_name VARCHAR(200),
                           customer_type VARCHAR(20) NOT NULL DEFAULT 'COMPANY',
                           nip VARCHAR(10),
                           regon VARCHAR(14),
                           street VARCHAR(200),
                           postal_code VARCHAR(10),
                           city VARCHAR(100),
                           phone VARCHAR(30),
                           email VARCHAR(200),
                           discount_percent DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
                           remarks TEXT,
                           PRIMARY KEY (id),
                           CONSTRAINT chk_customer_identity CHECK (
                               (
                                   customer_type = 'COMPANY'
                                       AND company_name IS NOT NULL
                                       AND TRIM(company_name) <> ''
                                   )
                                   OR
                               (
                                   customer_type = 'PERSON'
                                       AND first_name IS NOT NULL
                                       AND TRIM(first_name) <> ''
                                       AND last_name IS NOT NULL
                                       AND TRIM(last_name) <> ''
                                       AND company_name IS NULL
                                   )
                               ),
                           CONSTRAINT chk_person_no_company_ids CHECK (
                               customer_type <> 'PERSON'
                                   OR (nip IS NULL AND regon IS NULL)
                               )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shop_settings (
                               id TINYINT NOT NULL,
                               default_markup_percent DECIMAL(5, 2) NOT NULL,
                               PRIMARY KEY (id),
                               CONSTRAINT chk_default_markup
                                   CHECK (default_markup_percent >= 0 AND default_markup_percent <= 999.99),
                               CONSTRAINT chk_single_settings_row CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sales (
                       id BIGINT NOT NULL AUTO_INCREMENT,
                       customer_id BIGINT,
                       sold_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       remarks TEXT,
                       PRIMARY KEY (id),
                       KEY fk_sales_customer (customer_id),
                       CONSTRAINT fk_sales_customer
                           FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sale_items (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            sale_id BIGINT NOT NULL,
                            product_id BIGINT NOT NULL,
                            product_name VARCHAR(200) NOT NULL,
                            quantity INT NOT NULL,
                            unit_purchase_price_net DECIMAL(12, 2) NOT NULL,
                            unit_sale_price_net DECIMAL(12, 2) NOT NULL,
                            discount_percent DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
                            vat_rate DECIMAL(5, 2) NOT NULL,
                            active TINYINT(1) NOT NULL DEFAULT 1,
                            PRIMARY KEY (id),
                            KEY fk_item_sale (sale_id),
                            KEY fk_item_product (product_id),
                            CONSTRAINT fk_item_product
                                FOREIGN KEY (product_id) REFERENCES products (id),
                            CONSTRAINT fk_item_sale
                                FOREIGN KEY (sale_id) REFERENCES sales (id),
                            CONSTRAINT chk_item_discount CHECK (discount_percent BETWEEN 0 AND 100),
                            CONSTRAINT chk_item_prices
                                CHECK (unit_purchase_price_net >= 0 AND unit_sale_price_net >= 0),
                            CONSTRAINT chk_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_movements (
                                 id BIGINT NOT NULL AUTO_INCREMENT,
                                 product_id BIGINT NOT NULL,
                                 quantity_change INT NOT NULL,
                                 movement_type VARCHAR(20) NOT NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 remarks TEXT,
                                 sale_item_id BIGINT,
                                 unit_purchase_price_net DECIMAL(12, 2),
                                 markup_percent DECIMAL(7, 2),
                                 unit_sale_price_net DECIMAL(12, 2),
                                 PRIMARY KEY (id),
                                 UNIQUE KEY uq_movement_sale_item (sale_item_id),
                                 KEY fk_stock_product (product_id),
                                 CONSTRAINT fk_movement_sale_item
                                     FOREIGN KEY (sale_item_id) REFERENCES sale_items (id),
                                 CONSTRAINT fk_stock_product
                                     FOREIGN KEY (product_id) REFERENCES products (id),
                                 CONSTRAINT chk_movement_type CHECK (
                                     movement_type IN ('OPENING', 'DELIVERY', 'SALE', 'RETURN', 'CORRECTION')
                                     ),
                                 CONSTRAINT chk_stock_change CHECK (quantity_change <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO shop_settings (id, default_markup_percent)
VALUES (1, 25.00);