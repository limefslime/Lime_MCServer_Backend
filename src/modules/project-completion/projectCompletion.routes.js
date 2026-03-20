import { Router } from "express";
import {
  completeProjectController,
  getCompletedProjectsController,
  getActiveEffectsController,
  getProjectCompletionStatusController,
} from "./projectCompletion.controller.js";

const router = Router();

router.post("/:projectId/complete", completeProjectController);
router.get("/effects", getActiveEffectsController);
router.get("/completed", getCompletedProjectsController);
router.get("/:projectId/status", getProjectCompletionStatusController);

export default router;
