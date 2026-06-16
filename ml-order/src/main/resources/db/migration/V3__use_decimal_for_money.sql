UPDATE `order`
SET total_amount = 0
WHERE total_amount IS NULL;

UPDATE `order`
SET pay_amount = 0
WHERE pay_amount IS NULL;

UPDATE order_detail
SET course_price = 0
WHERE course_price IS NULL;

UPDATE cart
SET course_price = 0
WHERE course_price IS NULL;

ALTER TABLE `order`
    MODIFY COLUMN total_amount DECIMAL(12, 2) NOT NULL,
    MODIFY COLUMN pay_amount DECIMAL(12, 2) NOT NULL;

ALTER TABLE order_detail
    MODIFY COLUMN course_price DECIMAL(12, 2) NOT NULL;

ALTER TABLE cart
    MODIFY COLUMN course_price DECIMAL(12, 2) NOT NULL;
