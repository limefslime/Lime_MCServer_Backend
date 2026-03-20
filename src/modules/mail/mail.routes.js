import { Router } from "express";
import { claimMailController, getMailboxController, sendMailController } from "./mail.controller.js";

const router = Router();

router.post("/send", sendMailController);
router.get("/:playerId", getMailboxController);
router.post("/:mailId/claim", claimMailController);

export default router;
