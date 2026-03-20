import { getOperationsSummary } from "./ops.service.js";

export async function getOperationsSummaryHandler(_req, res) {
  try {
    const summary = await getOperationsSummary();
    res.status(200).json(summary);
  } catch (error) {
    console.error("[ops.controller] getOperationsSummary error", error);
    res.status(500).json({ message: "internal server error" });
  }
}

