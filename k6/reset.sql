-- 매 회차 전에 실행: 주문/구매 수/알림을 지우고 재고를 처음 상태(판매 0개)로 되돌린다 (ticketonline_k6 전용)
-- Redis(C 방식) 재고 키도 함께 비워야 한다:  C:\redis\redis-cli.exe -n 2 FLUSHDB

DELETE FROM message;
DELETE FROM product_order;
DELETE FROM purchase_count;
UPDATE sale_item SET sold_quantity = 0, version = 0;

-- 실행 뒤 확인용 (k6 가 끝나면 이 쿼리로 초과 판매가 없는지 본다)
-- SELECT s.total_quantity, s.sold_quantity,
--        (SELECT COALESCE(SUM(quantity), 0) FROM product_order WHERE status = 'SUCCESS') AS ordered_quantity,
--        (SELECT COUNT(*) FROM product_order WHERE status = 'SUCCESS') AS order_count
-- FROM sale_item s;
