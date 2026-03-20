import { Router } from "express";
import {
  addMoneyController,
  getWalletController,
  subtractMoneyController,
} from "./wallet.controller.js";

const router = Router();

router.get("/:playerId", getWalletController);
router.post("/add", addMoneyController);
router.post("/subtract", subtractMoneyController);

export default router;
