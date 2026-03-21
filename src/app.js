import "dotenv/config";
import express from "express";
import adminRoutes from "./modules/admin/admin.routes.js";
import eventRoutes from "./modules/event/event.routes.js";
import focusRoutes from "./modules/focus/focus.routes.js";
import investRoutes from "./modules/invest/invest.routes.js";
import {
  startStockPriceTicker,
  stopStockPriceTicker,
} from "./modules/invest/stockPriceTicker.service.js";
import mailRoutes from "./modules/mail/mail.routes.js";
import opsRoutes from "./modules/ops/ops.routes.js";
import projectCompletionRoutes from "./modules/project-completion/projectCompletion.routes.js";
import regionRoutes from "./modules/region/region.routes.js";
import {
  startShopStockReplenisher,
  stopShopStockReplenisher,
} from "./modules/shop/shopReplenisher.service.js";
import shopRoutes from "./modules/shop/shop.routes.js";
import walletRoutes from "./modules/wallet/wallet.routes.js";

const app = express();

// JSON 본문 파싱 (POST /wallet/add, /wallet/subtract)
app.use(express.json());

// 간단한 상태 확인용 엔드포인트
app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.use("/wallet", walletRoutes);
app.use("/shop", shopRoutes);
app.use("/events", eventRoutes);
app.use("/focus", focusRoutes);
app.use("/regions", regionRoutes);
app.use("/invest", investRoutes);
app.use("/mail", mailRoutes);
app.use("/ops", opsRoutes);
app.use("/project-completion", projectCompletionRoutes);
app.use("/admin", adminRoutes);

app.use((req, res) => {
  res.status(404).json({ message: "not found" });
});

// 예기치 않은 오류를 JSON 형태로 일관되게 반환
app.use((err, _req, res, _next) => {
  console.error("[app] unhandled error", err);
  res.status(500).json({ message: "internal server error" });
});

const port = process.env.PORT ? Number(process.env.PORT) : 3000;

const shopReplenisher = startShopStockReplenisher({ logger: console });
if (shopReplenisher.started) {
  console.log(
    `[app] shop stock replenisher started (interval=${shopReplenisher.intervalMs}ms, batch=${shopReplenisher.batchLimit})`
  );
}

const stockTicker = startStockPriceTicker({ logger: console });
if (stockTicker.started) {
  console.log(`[app] stock ticker started (interval=${stockTicker.intervalMs}ms)`);
}

const server = app.listen(port, () => {
  console.log(`[app] wallet api server listening on port ${port}`);
});

function shutdown(signal) {
  console.log(`[app] received ${signal}, shutting down...`);
  stopShopStockReplenisher();
  stopStockPriceTicker();
  server.close(() => {
    console.log("[app] server closed");
    process.exit(0);
  });
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
