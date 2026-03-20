import {
  activateEvent,
  createEvent,
  endEvent,
  eventErrorCode,
  getActiveEvents,
  getEventDetail,
  getEvents,
  isEventServiceError,
} from "./event.service.js";

function handleEventError(error, res) {
  if (!isEventServiceError(error)) {
    console.error("[event.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  if (error.code === eventErrorCode.INVALID_INPUT) {
    res.status(400).json({ message: error.message });
    return;
  }

  if (error.code === eventErrorCode.EVENT_NOT_FOUND) {
    res.status(404).json({ message: error.message });
    return;
  }

  if (error.code === eventErrorCode.INVALID_STATUS_TRANSITION) {
    res.status(409).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

export async function createEventController(req, res) {
  try {
    const result = await createEvent(req.body);
    res.json(result);
  } catch (error) {
    handleEventError(error, res);
  }
}

export async function getEventsController(_req, res) {
  try {
    const result = await getEvents();
    res.json(result);
  } catch (error) {
    handleEventError(error, res);
  }
}

export async function getEventDetailController(req, res) {
  try {
    const result = await getEventDetail(req.params.id);
    res.json(result);
  } catch (error) {
    handleEventError(error, res);
  }
}

export async function activateEventController(req, res) {
  try {
    const result = await activateEvent(req.params.id);
    res.json(result);
  } catch (error) {
    handleEventError(error, res);
  }
}

export async function endEventController(req, res) {
  try {
    const result = await endEvent(req.params.id);
    res.json(result);
  } catch (error) {
    handleEventError(error, res);
  }
}

export async function getActiveEventsController(_req, res) {
  try {
    const result = await getActiveEvents();
    res.json(result);
  } catch (error) {
    handleEventError(error, res);
  }
}
