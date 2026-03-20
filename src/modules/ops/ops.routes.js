import { Router } from "express";
import { getOperationsSummaryHandler } from "./ops.controller.js";

const router = Router();

router.get("/summary", getOperationsSummaryHandler);

export default router;

