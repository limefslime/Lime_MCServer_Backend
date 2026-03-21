import { Router } from "express";
import {
  donateVillageFundController,
  getVillageFundController,
} from "./village.controller.js";

const router = Router();

router.get("/fund", getVillageFundController);
router.post("/fund/donate", donateVillageFundController);

export default router;
