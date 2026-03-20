import { Router } from "express";
import {
  getCurrentFocusController,
  recalculateFocusController,
} from "./focus.controller.js";

const router = Router();

router.get("/current", getCurrentFocusController);
router.post("/recalculate", recalculateFocusController);

export default router;
