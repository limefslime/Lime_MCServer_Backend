import {
  getAdminActiveProjectEffects,
  getAdminActiveEvents,
  getAdminCurrentFocus,
  getAdminMailStats,
  getAdminProjectCompletions,
  getAdminSummary,
} from "./admin.service.js";

function handleAdminError(error, res) {
  console.error("[admin.controller] unexpected error", error);
  res.status(500).json({ message: "internal server error" });
}

export async function getAdminSummaryController(_req, res) {
  try {
    const result = await getAdminSummary();
    res.json(result);
  } catch (error) {
    handleAdminError(error, res);
  }
}

export async function getAdminProjectCompletionsController(_req, res) {
  try {
    const result = await getAdminProjectCompletions();
    res.json(result);
  } catch (error) {
    handleAdminError(error, res);
  }
}

export async function getAdminActiveEventsController(_req, res) {
  try {
    const result = await getAdminActiveEvents();
    res.json(result);
  } catch (error) {
    handleAdminError(error, res);
  }
}

export async function getAdminCurrentFocusController(_req, res) {
  try {
    const result = await getAdminCurrentFocus();
    res.json(result);
  } catch (error) {
    handleAdminError(error, res);
  }
}

export async function getAdminMailStatsController(_req, res) {
  try {
    const result = await getAdminMailStats();
    res.json(result);
  } catch (error) {
    handleAdminError(error, res);
  }
}

export async function getAdminActiveProjectEffectsController(_req, res) {
  try {
    const result = await getAdminActiveProjectEffects();
    res.json(result);
  } catch (error) {
    handleAdminError(error, res);
  }
}
