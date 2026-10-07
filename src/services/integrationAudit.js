import { mkdir, open } from 'node:fs/promises';
import { resolve } from 'node:path';
import { withTransaction } from '../db/pool.js';

// At-least-once file delivery: auditId permits deduplication after a crash
// between fsync and the database acknowledgement. Files are never auto-deleted.
export async function flushIntegrationAudit(transaction = withTransaction) {
  return transaction(async client => {
    const { rows } = await client.query(
      'SELECT id,payload,created_at FROM integration_audit_outbox WHERE written_at IS NULL ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 100');
    if (!rows.length) return;
    const directory = resolve(process.env.AUDIT_LOG_DIR || 'logs/audit');
    await mkdir(directory, { recursive: true });
    for (const row of rows) {
      const day = row.created_at.toISOString().slice(0, 10);
      const file = await open(resolve(directory, `integration-${day}.jsonl`), 'a');
      try {
        await file.writeFile(JSON.stringify({ auditId: String(row.id), recordedAt: row.created_at, ...row.payload }) + '\n');
        await file.sync();
      } finally { await file.close(); }
      await client.query('UPDATE integration_audit_outbox SET written_at=NOW() WHERE id=$1', [row.id]);
    }
  });
}
export function startIntegrationAudit() {
  let running = false, lastWarning = 0;
  const timer = setInterval(async () => {
    if (running) return;
    running = true;
    try { await flushIntegrationAudit(); }
    catch (_) {
      if (Date.now() - lastWarning > 60000) {
        console.error('[integration] audit pending; check migration 019, database and AUDIT_LOG_DIR');
        lastWarning = Date.now();
      }
    } finally { running = false; }
  }, 5000);
  timer.unref();
  return () => clearInterval(timer);
}
