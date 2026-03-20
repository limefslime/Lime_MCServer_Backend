import { Router } from "express";
import {
  getAdminActiveProjectEffectsController,
  getAdminActiveEventsController,
  getAdminCurrentFocusController,
  getAdminMailStatsController,
  getAdminProjectCompletionsController,
  getAdminSummaryController,
} from "./admin.controller.js";

const router = Router();

router.get("/summary", getAdminSummaryController);
router.get("/project-completions", getAdminProjectCompletionsController);
router.get("/events/active", getAdminActiveEventsController);
router.get("/project-effects/active", getAdminActiveProjectEffectsController);
router.get("/focus/current", getAdminCurrentFocusController);
router.get("/mail/stats", getAdminMailStatsController);

export default router;
