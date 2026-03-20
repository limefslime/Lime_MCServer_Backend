import { Router } from "express";
import {
  activateEventController,
  createEventController,
  endEventController,
  getActiveEventsController,
  getEventDetailController,
  getEventsController,
} from "./event.controller.js";

const router = Router();

router.post("/", createEventController);
router.get("/", getEventsController);
router.get("/active", getActiveEventsController);
router.get("/:id", getEventDetailController);
router.post("/:id/activate", activateEventController);
router.post("/:id/end", endEventController);

export default router;
