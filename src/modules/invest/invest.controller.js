import {
  createProject,
  getProjectProgress,
  getProjectDetail,
  getProjects,
  investErrorCode,
  investToProject,
  isInvestServiceError,
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

  if (error.code === investErrorCode.PROJECT_NOT_FOUND) {
    res.status(404).json({ message: error.message });
    return;
  }

  if (
    error.code === investErrorCode.PROJECT_NOT_ACTIVE ||
    error.code === investErrorCode.INSUFFICIENT_BALANCE
  ) {
    res.status(409).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

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
