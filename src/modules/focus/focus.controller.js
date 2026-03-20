import { getCurrentFocus, recalculateFocus } from "./focus.service.js";

function handleFocusError(scope, error, res) {
  console.error(`[focus.controller] ${scope} error`, error);
  res.status(500).json({ message: "internal server error" });
}

export async function getCurrentFocusController(_req, res) {
  try {
    const result = await getCurrentFocus();
    res.json(result);
  } catch (error) {
    handleFocusError("getCurrentFocus", error, res);
  }
}

export async function recalculateFocusController(_req, res) {
  try {
    const result = await recalculateFocus();
    res.json(result);
  } catch (error) {
    handleFocusError("recalculateFocus", error, res);
  }
}
