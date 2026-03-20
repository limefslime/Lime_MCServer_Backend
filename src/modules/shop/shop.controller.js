import {
  buyItem,
  getShopItem,
  isShopServiceError,
  listShopItems,
  previewBuyItem,
  previewSellItem,
  sellItem,
  shopErrorCode,
} from "./shop.service.js";

function handleShopError(error, res) {
  if (!isShopServiceError(error)) {
    console.error("[shop.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  if (
    error.code === shopErrorCode.INVALID_INPUT ||
    error.code === shopErrorCode.ITEM_PRICE_NOT_TRADABLE ||
    error.code === shopErrorCode.SELL_QUANTITY_TOO_LARGE
  ) {
    res.status(400).json({ message: error.message });
    return;
  }

  if (error.code === shopErrorCode.ITEM_NOT_FOUND) {
    res.status(404).json({ message: error.message });
    return;
  }

  if (
    error.code === shopErrorCode.ITEM_INACTIVE ||
    error.code === shopErrorCode.INSUFFICIENT_BALANCE ||
    error.code === shopErrorCode.INSUFFICIENT_STOCK
  ) {
    res.status(409).json({ message: error.message });
    return;
  }

  if (error.code === shopErrorCode.TRADE_COOLDOWN_ACTIVE) {
    res.status(429).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

export async function listShopItemsController(_req, res) {
  try {
    const result = await listShopItems();
    res.json(result);
  } catch (error) {
    handleShopError(error, res);
  }
}

export async function getShopItemController(req, res) {
  try {
    const result = await getShopItem(req.params.itemId);
    res.json(result);
  } catch (error) {
    handleShopError(error, res);
  }
}

export async function buyItemController(req, res) {
  try {
    const result = await buyItem(req.body);
    res.json(result);
  } catch (error) {
    handleShopError(error, res);
  }
}

export async function sellItemController(req, res) {
  try {
    const result = await sellItem(req.body);
    res.json(result);
  } catch (error) {
    handleShopError(error, res);
  }
}

export async function previewBuyItemHandler(req, res) {
  try {
    const result = await previewBuyItem(req.body);
    res.json(result);
  } catch (error) {
    handleShopError(error, res);
  }
}

export async function previewSellItemHandler(req, res) {
  try {
    const result = await previewSellItem(req.body);
    res.json(result);
  } catch (error) {
    handleShopError(error, res);
  }
}
