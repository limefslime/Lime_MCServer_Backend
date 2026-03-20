import { getActiveEvents } from "../event/event.service.js";
import { getCurrentFocus } from "../focus/focus.service.js";
import { getMailStats } from "../mail/mail.service.js";
import { getOperationsSummary } from "../ops/ops.service.js";
import {
  getActiveEffects,
  getCompletedProjects,
} from "../project-completion/projectCompletion.service.js";

function normalizeArray(value) {
  return Array.isArray(value) ? value : [];
}

export async function getAdminSummary() {
  return getOperationsSummary();
}

export async function getAdminProjectCompletions() {
  const result = await getCompletedProjects();
  const items = normalizeArray(result?.items);

  return {
    itemCount: items.length,
    items,
  };
}

export async function getAdminActiveEvents() {
  const items = normalizeArray(await getActiveEvents());
  return {
    activeCount: items.length,
    items,
  };
}

export async function getAdminCurrentFocus() {
  return getCurrentFocus();
}

export async function getAdminMailStats() {
  return getMailStats();
}

export async function getAdminActiveProjectEffects() {
  const items = normalizeArray(await getActiveEffects());
  return {
    activeCount: items.length,
    items,
  };
}
