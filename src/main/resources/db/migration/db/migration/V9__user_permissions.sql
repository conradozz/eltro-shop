CREATE TABLE user_permissions (
                                  user_id BIGINT NOT NULL,
                                  permission_code VARCHAR(50) NOT NULL,

                                  PRIMARY KEY (user_id, permission_code),

                                  CONSTRAINT fk_user_permission_user
                                      FOREIGN KEY (user_id) REFERENCES users(id),

                                  CONSTRAINT chk_user_permission_code CHECK (
                                      permission_code IN (
                                                          'PRODUCT_CREATE',
                                                          'PRODUCT_EDIT',
                                                          'PRODUCT_PRICE_EDIT',
                                                          'CUSTOMER_CREATE',
                                                          'CUSTOMER_EDIT',
                                                          'CUSTOMER_DISCOUNT_EDIT',
                                                          'DELIVERY_CREATE',
                                                          'DELIVERY_EDIT',
                                                          'SALE_CREATE',
                                                          'SALE_EDIT',
                                                          'ORDER_MANAGE',
                                                          'DOCUMENT_CREATE',
                                                          'REPORT_ALL'
                                          )
                                      )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Zachowanie dotychczasowych uprawnien obecnych sprzedawcow.
-- Dodatkowe administrator bedzie mogl wlaczyc w panelu.

INSERT INTO user_permissions (user_id, permission_code)
SELECT u.id, p.permission_code
FROM users u
         CROSS JOIN (
    SELECT 'CUSTOMER_CREATE' AS permission_code
    UNION ALL SELECT 'CUSTOMER_EDIT'
    UNION ALL SELECT 'CUSTOMER_DISCOUNT_EDIT'
    UNION ALL SELECT 'DELIVERY_CREATE'
    UNION ALL SELECT 'SALE_CREATE'
    UNION ALL SELECT 'ORDER_MANAGE'
    UNION ALL SELECT 'DOCUMENT_CREATE'
) p
WHERE u.role = 'SELLER';