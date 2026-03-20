import {
  getAllRegions,
  getRegion,
  isRegionServiceError,
  recalculateRegions,
  regionErrorCode,
} from "./region.service.js";

function handleRegionError(error, res) {
  if (!isRegionServiceError(error)) {
    console.error("[region.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  if (error.code === regionErrorCode.INVALID_REGION) {
    res.status(400).json({ message: error.message });
    return;
  }

  if (error.code === regionErrorCode.REGION_NOT_FOUND) {
    res.status(404).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

export async function getAllRegionsController(_req, res) {
  try {
    const result = await getAllRegions();
    res.json(result);
  } catch (error) {
    handleRegionError(error, res);
  }
}

export async function getRegionController(req, res) {
  try {
    const result = await getRegion(req.params.region);
    res.json(result);
  } catch (error) {
    handleRegionError(error, res);
  }
}

export async function recalculateRegionsController(_req, res) {
  try {
    const result = await recalculateRegions();
    res.json(result);
  } catch (error) {
    handleRegionError(error, res);
  }
}
