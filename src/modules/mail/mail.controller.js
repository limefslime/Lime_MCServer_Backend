import {
  claimMail,
  getMailbox,
  isMailServiceError,
  mailErrorCode,
  sendMail,
} from "./mail.service.js";

function handleMailError(error, res, request = null) {
  if (!isMailServiceError(error)) {
    console.error("[mail.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  const response = { message: error.message };
  if (request?.requestId !== undefined) Object.assign(response, {
    code: error.code, playerId: request.playerId, requestId: request.requestId, mailId: request.mailId,
  });

  if (error.code === mailErrorCode.INVALID_INPUT) {
    res.status(400).json(response);
    return;
  }

  if (error.code === mailErrorCode.MAIL_NOT_FOUND) {
    res.status(404).json(response);
    return;
  }

  if (error.code === mailErrorCode.MAIL_ALREADY_CLAIMED ||
      error.code === mailErrorCode.REQUEST_CONFLICT || error.code === mailErrorCode.INVALID_ITEM_REWARD) {
    res.status(409).json(response);
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
    const result = await claimMail(req.params.mailId, req.body ?? {});
    res.json(result);
  } catch (error) {
    handleMailError(error, res, { ...req.body, mailId: req.params.mailId });
  }
}
