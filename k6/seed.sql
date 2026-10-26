-- k6 부하 테스트용 데이터 (k6 전용 DB ticketonline_k6 에서만 실행)
-- 테이블은 서버를 ticketonline_k6 로 한 번 띄우면 자동으로 만들어진다 (ddl-auto: update)
--
-- 회원: 판매자 k6-seller@test.com 1명 + 구매자 k6-0001@test.com ~ k6-1000@test.com
-- 비밀번호(모두 같음, 테스트 전용): k6-test-pass1
-- 상품: 굿즈 "k6 부하 테스트 굿즈" 재고 100, 1인 1개, 현장 수령, 지금 판매 중

SET NAMES utf8mb4;

INSERT INTO member (email, password, name, role, created_at)
VALUES ('k6-seller@test.com', '$2a$10$oAK418Y21wMXzMoc/X30nut4hGWyVwiTIO2gZDACdIpSTv89Q6PL.', 'k6 판매자', 'USER', NOW());

SET SESSION cte_max_recursion_depth = 2000;
INSERT INTO member (email, password, name, role, created_at)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1000)
SELECT CONCAT('k6-', LPAD(n, 4, '0'), '@test.com'),
       '$2a$10$oAK418Y21wMXzMoc/X30nut4hGWyVwiTIO2gZDACdIpSTv89Q6PL.',
       CONCAT('k6 구매자 ', n), 'USER', NOW()
FROM seq;

INSERT INTO product (seller_id, type, title, image_url, max_per_person, sale_start_at, sale_end_at, created_at)
SELECT member_id, 'GOODS', 'k6 부하 테스트 굿즈', NULL, 1,
       NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 30 DAY, NOW()
FROM member WHERE email = 'k6-seller@test.com';

SET @product_id = LAST_INSERT_ID();

INSERT INTO goods_detail (product_id, description, delivery_type)
VALUES (@product_id, 'k6 부하 테스트용 상품', 'PICKUP');

INSERT INTO sale_item (product_id, performance_id, seat_grade_id, price, total_quantity, sold_quantity, version)
VALUES (@product_id, NULL, NULL, 10000, 100, 0, 0);

SELECT @product_id AS product_id;
