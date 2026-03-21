import {
  buyStock,
  createProject,
  getProjectDetail,
  getProjectProgress,
  getProjects,
  getStockDetail,
  investErrorCode,
  investToProject,
  isInvestServiceError,
  listStocks,
  sellStock,
} from "./invest.service.js";

function handleInvestError(error, res) {
  if (!isInvestServiceError(error)) {
    console.error("[invest.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  if (error.code === investErrorCode.INVALID_INPUT) {
    res.status(400).json({ message: error.message });
    return;
  }

  if (
    error.code === investErrorCode.PROJECT_NOT_FOUND ||
    error.code === investErrorCode.STOCK_NOT_FOUND
  ) {
    res.status(404).json({ message: error.message });
    return;
  }

  if (
    error.code === investErrorCode.PROJECT_NOT_ACTIVE ||
    error.code === investErrorCode.INSUFFICIENT_BALANCE ||
    error.code === investErrorCode.INSUFFICIENT_QUANTITY
  ) {
    res.status(409).json({ message: error.message });
    return;
  }

  if (error.code === investErrorCode.SERVICE_DISABLED) {
    res.status(503).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

export async function getStocksController(req, res) {
  try {
    const playerId = typeof req.query?.playerId === "string" ? req.query.playerId : null;
    const result = await listStocks({ playerId });
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}

export async function getStockDetailController(req, res) {
  try {
    const playerId = typeof req.query?.playerId === "string" ? req.query.playerId : null;
    const result = await getStockDetail(req.params.id, { playerId });
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}

export async function buyStockController(req, res) {
  try {
    const result = await buyStock({
      playerId: req.body?.playerId,
      stockId: req.params.id,
      quantity: req.body?.quantity,
    });
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}

export async function sellStockController(req, res) {
  try {
    const result = await sellStock({
      playerId: req.body?.playerId,
      stockId: req.params.id,
      quantity: req.body?.quantity,
    });
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}

// Legacy project-based handlers are intentionally preserved to keep route contracts stable.
export async function createProjectController(req, res) {
  try {
    const result = await createProject(req.body);
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}

export async function getProjectsController(_req, res) {
  try {
    const result = await getProjects();
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}

export async function getProjectDetailController(req, res) {
  try {
    const result = await getProjectDetail(req.params.id);
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}

export async function investToProjectController(req, res) {
  try {
    const result = await investToProject(req.params.id, req.body);
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}

export async function getProjectProgressController(req, res) {
  try {
    const result = await getProjectProgress(req.params.id);
    res.json(result);
  } catch (error) {
    handleInvestError(error, res);
  }
}
