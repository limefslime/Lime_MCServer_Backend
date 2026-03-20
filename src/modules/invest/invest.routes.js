import { Router } from "express";
import {
  createProjectController,
  getProjectDetailController,
  getProjectProgressController,
  getProjectsController,
  investToProjectController,
} from "./invest.controller.js";

const router = Router();

router.post("/projects", createProjectController);
router.get("/projects", getProjectsController);
router.get("/projects/:id", getProjectDetailController);
router.get("/projects/:id/progress", getProjectProgressController);
router.post("/projects/:id/invest", investToProjectController);

export default router;
