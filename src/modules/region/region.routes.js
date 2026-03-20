import { Router } from "express";
import {
  getAllRegionsController,
  getRegionController,
  recalculateRegionsController,
} from "./region.controller.js";

const router = Router();

router.get("/", getAllRegionsController);
router.get("/:region", getRegionController);
router.post("/recalculate", recalculateRegionsController);

export default router;
