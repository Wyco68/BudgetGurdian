import * as refills from '../services/refillService.js';

export async function list(_req, res) {
  res.json(await refills.listRefillItems());
}

export async function upsert(req, res) {
  res.json(await refills.upsertRefillItem(req.params.name, req.body));
}

export async function remove(req, res) {
  await refills.deleteRefillItem(req.params.name);
  res.status(204).end();
}
