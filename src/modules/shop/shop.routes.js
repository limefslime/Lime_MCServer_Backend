import { Router } from "express";
import {
  buyItemController,
  getShopItemController,
  listShopItemsController,
  previewBuyItemHandler,
  previewSellItemHandler,
  sellItemController,
} from "./shop.controller.js";

const router = Router();

router.get("/items", listShopItemsController);
router.get("/items/:itemId", getShopItemController);
router.post("/buy/preview", previewBuyItemHandler);
router.post("/buy", buyItemController);
router.post("/sell/preview", previewSellItemHandler);
router.post("/sell", sellItemController);

export default router;
