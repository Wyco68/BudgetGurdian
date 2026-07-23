import * as bills from '../services/billService.js';
import { numericId } from '../utils/params.js';

export async function list(_req, res) {
  res.json(await bills.listBills());
}

export async function create(req, res) {
  res.status(201).json(await bills.createBill(req.body));
}

export async function updateLastPaid(req, res) {
  res.json(await bills.updateBillLastPaid(numericId(req.params.id), req.body));
}

export async function remove(req, res) {
  await bills.deleteBill(numericId(req.params.id));
  res.status(204).end();
}
