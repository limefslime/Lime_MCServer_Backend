import {
  claimMail,
  getMailbox,
  isMailServiceError,
  mailErrorCode,
  sendMail,
} from "./mail.service.js";

function handleMailError(error, res) {
  if (!isMailServiceError(error)) {
    console.error("[mail.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  if (error.code === mailErrorCode.INVALID_INPUT) {
    res.status(400).json({ message: error.message });
    return;
  }

  if (error.code === mailErrorCode.MAIL_NOT_FOUND) {
    res.status(404).json({ message: error.message });
    return;
  }

  if (error.code === mailErrorCode.MAIL_ALREADY_CLAIMED) {
    res.status(409).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

export async function sendMailController(req, res) {
  try {
    const result = await sendMail(req.body);
    res.json(result);
  } catch (error) {
    handleMailError(error, res);
  }
}

export async function getMailboxController(req, res) {
  try {
    const result = await getMailbox(req.params.playerId);
    res.json(result);
  } catch (error) {
    handleMailError(error, res);
  }
}

export async function claimMailController(req, res) {
  try {
    const result = await claimMail(req.params.mailId);
    res.json(result);
  } catch (error) {
    handleMailError(error, res);
  }
}
