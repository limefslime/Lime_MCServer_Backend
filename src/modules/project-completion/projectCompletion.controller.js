import {
  completeProject,
  getCompletedProjects,
  getProjectCompletionStatus,
  getActiveEffects,
  isProjectCompletionServiceError,
  projectCompletionErrorCode,
} from "./projectCompletion.service.js";

function handleProjectCompletionError(error, res) {
  if (!isProjectCompletionServiceError(error)) {
    console.error("[projectCompletion.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  if (error.code === projectCompletionErrorCode.INVALID_INPUT) {
    res.status(400).json({ message: error.message });
    return;
  }

  if (error.code === projectCompletionErrorCode.PROJECT_NOT_FOUND) {
    res.status(404).json({ message: error.message });
    return;
  }

  if (
    error.code === projectCompletionErrorCode.PROJECT_NOT_ACTIVE ||
    error.code === projectCompletionErrorCode.TARGET_NOT_REACHED ||
    error.code === projectCompletionErrorCode.EFFECT_ALREADY_EXISTS
  ) {
    res.status(409).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

export async function completeProjectController(req, res) {
  try {
    const result = await completeProject(req.params.projectId);
    const completionStatus = await getProjectCompletionStatus(req.params.projectId);
    res.json({
      ...result,
      completionStatus,
    });
  } catch (error) {
    handleProjectCompletionError(error, res);
  }
}

export async function getActiveEffectsController(_req, res) {
  try {
    const result = await getActiveEffects();
    res.json(result);
  } catch (error) {
    handleProjectCompletionError(error, res);
  }
}

export async function getProjectCompletionStatusController(req, res) {
  try {
    const result = await getProjectCompletionStatus(req.params.projectId);
    res.json(result);
  } catch (error) {
    handleProjectCompletionError(error, res);
  }
}

export async function getCompletedProjectsController(_req, res) {
  try {
    const result = await getCompletedProjects();
    res.json(result);
  } catch (error) {
    handleProjectCompletionError(error, res);
  }
}
