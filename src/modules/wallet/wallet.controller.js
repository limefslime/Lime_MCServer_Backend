import {
  addMoney,
  getWallet,
  isWalletServiceError,
  subtractMoney,
  walletErrorCode,
} from "./wallet.service.js";

function handleWalletError(error, res) {
  if (!isWalletServiceError(error)) {
    console.error("[wallet.controller] unexpected error", error);
    res.status(500).json({ message: "internal server error" });
    return;
  }

  if (error.code === walletErrorCode.INVALID_INPUT) {
    res.status(400).json({ message: error.message });
    return;
  }

  if (error.code === walletErrorCode.INSUFFICIENT_BALANCE) {
    res.status(409).json({ message: error.message });
    return;
  }

  res.status(500).json({ message: "internal server error" });
}

export async function getWalletController(req, res) {
  try {
    const result = await getWallet(req.params.playerId);
    res.json(result);
  } catch (error) {
    handleWalletError(error, res);
  }
}

export async function addMoneyController(req, res) {
  try {
    const result = await addMoney(req.body);
    res.json(result);
  } catch (error) {
    handleWalletError(error, res);
  }
}

export async function subtractMoneyController(req, res) {
  try {
    const result = await subtractMoney(req.body);
    res.json(result);
  } catch (error) {
    handleWalletError(error, res);
  }
}
