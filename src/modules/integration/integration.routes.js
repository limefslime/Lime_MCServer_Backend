import { Router } from 'express';
import { timingSafeEqual } from 'node:crypto';
import { grantQuestReward } from './reward.service.js';
import { RewardError } from './reward.core.js';

export function requireIntegrationToken(req, res, next) {
  const token = process.env.INTEGRATION_API_TOKEN;
  if (!token || token.length < 32) return res.status(503).json({ message: 'integration token not configured' });
  const expected = Buffer.from(`Bearer ${token}`);
  const provided = Buffer.from(req.get('authorization') || '');
  if (provided.length !== expected.length || !timingSafeEqual(provided, expected)) {
    return res.status(401).json({ message: 'unauthorized' });
  }
  next();
}
const router = Router();
router.use(requireIntegrationToken);
router.post('/rewards', async (req, res) => {
  try { res.json(await grantQuestReward(req.body)); }
  catch (error) {
    if (error instanceof RewardError) return res.status(error.status).json({ message: error.message });
    console.error('[integration] reward failed:', error.code || error.name);
    res.status(503).json({ message: 'reward unavailable; retry with same requestId' });
  }
});
export default router;
