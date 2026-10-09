import "dotenv/config";
import express from "express";
import pool from "./db/pool.js";
import adminOperationsRoutes from "./modules/admin/admin.operations.routes.js";
import {requireIntegrationToken} from "./modules/integration/integration.routes.js";
import {loadEconomySettings} from "./modules/economy/economy.settings.js";
import integrationRoutes from "./modules/integration/integration.routes.js";
import { startIntegrationAudit } from "./services/integrationAudit.js";
import adminRoutes from "./modules/admin/admin.routes.js";
import eventRoutes from "./modules/event/event.routes.js";
import focusRoutes from "./modules/focus/focus.routes.js";
import investRoutes from "./modules/invest/invest.routes.js";
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
app.use("/integration", integrationRoutes);

// 간단한 상태 확인용 엔드포인트
app.get("/health", async (_req,res) => {try {await pool.query("SELECT 1");res.json({status:"ok"});}catch {res.status(503).json({status:"database_unavailable"});}});
app.use(requireIntegrationToken);
app.use("/admin/manage",adminOperationsRoutes);

app.post('/players/sync',async(req,res)=>{
 const {playerId,username}=req.body??{};
 if(typeof playerId!=='string'||!(/^[0-9a-f-]{36}$/i).test(playerId)||typeof username!=='string'||!(/^[A-Za-z0-9_]{1,16}$/).test(username))return res.status(400).json({message:'Invalid player identity'});
 const client=await pool.connect();try{await client.query('BEGIN');await client.query('UPDATE players SET username=NULL WHERE username=$1 AND id<>$2',[username,playerId]);await client.query('INSERT INTO players(id,username) VALUES($1,$2) ON CONFLICT(id) DO UPDATE SET username=EXCLUDED.username',[playerId,username]);await client.query('COMMIT');res.json({playerId,username});}catch(e){await client.query('ROLLBACK');res.status(503).json({message:'Player sync unavailable'});}finally{client.release();}
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

await loadEconomySettings();
const server = app.listen(port, () => {
  console.log(`[app] wallet api server listening on port ${port}`);
});
const stopIntegrationAudit = startIntegrationAudit();

function shutdown(signal) {
  stopIntegrationAudit();
  console.log(`[app] received ${signal}, shutting down...`);
  stopShopStockReplenisher();
  server.close(() => {
    console.log("[app] server closed");
    process.exit(0);
  });
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
