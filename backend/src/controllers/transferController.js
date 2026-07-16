import * as transfers from '../services/transferService.js';
import { numericId } from '../utils/params.js';

export async function list(_req, res) {
  res.json(await transfers.listTransfers());
}

export async function create(req, res) {
  res.status(201).json(await transfers.createTransfer(req.body));
}

export async function remove(req, res) {
  await transfers.deleteTransfer(numericId(req.params.id));
  res.status(204).end();
}
