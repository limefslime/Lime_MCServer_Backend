import { Router } from "express";
import {
  buyStockController,
  createProjectController,
  getProjectDetailController,
  getProjectProgressController,
  getProjectsController,
  getStockDetailController,
  getStocksController,
  investToProjectController,
  sellStockController,
} from "./invest.controller.js";

const router = Router();

// Stock-style invest APIs.
router.get("/stocks", getStocksController);
router.get("/stocks/:id", getStockDetailController);
router.post("/stocks/:id/buy", buyStockController);
router.post("/stocks/:id/sell", sellStockController);

// Legacy project APIs kept for compatibility, but service-side behavior is disabled.
router.post("/projects", createProjectController);
router.get("/projects", getProjectsController);
router.get("/projects/:id", getProjectDetailController);
router.get("/projects/:id/progress", getProjectProgressController);
router.post("/projects/:id/invest", investToProjectController);

export default router;
