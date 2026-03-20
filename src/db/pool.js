import pg from "pg";

const { Pool } = pg;

// DATABASE_URL 또는 개별 환경 변수 방식 둘 다 지원
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  host: process.env.DB_HOST,
  port: process.env.DB_PORT ? Number(process.env.DB_PORT) : undefined,
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  max: process.env.DB_POOL_MAX ? Number(process.env.DB_POOL_MAX) : 10,
  idleTimeoutMillis: process.env.DB_IDLE_TIMEOUT_MS
    ? Number(process.env.DB_IDLE_TIMEOUT_MS)
    : 30000,
});

pool.on("error", (error) => {
  console.error("[db] unexpected idle client error", error);
});

/**
 * 트랜잭션 공통 래퍼.
 * 서비스 계층에서 "지갑 변경 + ledger 기록"을 하나의 원자 작업으로 실행할 때 사용한다.
 */
export async function withTransaction(work) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const result = await work(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

export default pool;
