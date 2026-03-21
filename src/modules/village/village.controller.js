import {
  donateVillageFund,
  getVillageFundStatus,
  isVillageServiceError,
  villageErrorCode,
} from "./village.service.js";

function handleVillageError(error, res) {
  if (!isVillageServiceError(error)) {
    console.error("[village.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  if (error.code === villageErrorCode.INVALID_INPUT) {
    res.status(400).json({ message: error.message });
    return;
  }

  if (error.code === villageErrorCode.INSUFFICIENT_BALANCE) {
    res.status(409).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

export async function getVillageFundController(req, res) {
  try {
    const result = await getVillageFundStatus({
      playerId: req.query?.playerId,
    });
    res.json(result);
  } catch (error) {
    handleVillageError(error, res);
  }
}

export async function donateVillageFundController(req, res) {
  try {
    const result = await donateVillageFund({
      playerId: req.body?.playerId,
      amount: req.body?.amount,
    });
    res.json(result);
  } catch (error) {
    handleVillageError(error, res);
  }
}
