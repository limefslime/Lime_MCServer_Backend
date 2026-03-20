-- 004_seed_shop_items.sql
-- 상점 샘플 데이터 5개
-- 요구 분포: agri 2, port 1, industry 1, misc 1

INSERT INTO shop_items (item_id, item_name, category, buy_price, sell_price, is_active)
VALUES
  ('minecraft:carrot', 'Carrot', 'agri', 30, 20, TRUE),
  ('minecraft:wheat', 'Wheat', 'agri', 25, 15, TRUE),
  ('aquaculture:atlantic_cod', 'Atlantic Cod', 'port', 45, 30, TRUE),
  ('minecraft:iron_ingot', 'Iron Ingot', 'industry', 80, 55, TRUE),
  ('minecraft:torch', 'Torch', 'misc', 10, 5, TRUE)
ON CONFLICT (item_id) DO UPDATE
SET
  item_name = EXCLUDED.item_name,
  category = EXCLUDED.category,
  buy_price = EXCLUDED.buy_price,
  sell_price = EXCLUDED.sell_price,
  is_active = EXCLUDED.is_active,
  updated_at = NOW();
