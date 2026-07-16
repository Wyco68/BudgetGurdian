import * as debts from '../services/debtService.js';
import { numericId } from '../utils/params.js';

export async function list(_req, res) {
  res.json(await debts.listDebts());
}

export async function listPayments(_req, res) {
  res.json(await debts.listAllPayments());
}

export async function create(req, res) {
  res.status(201).json(await debts.createDebt(req.body));
}

export async function updateStatus(req, res) {
  res.json(await debts.updateDebtStatus(numericId(req.params.id), req.body));
}

export async function remove(req, res) {
  await debts.deleteDebt(numericId(req.params.id));
  res.status(204).end();
}

export async function createPayment(req, res) {
  res.status(201).json(await debts.createPayment(numericId(req.params.id), req.body));
}

export async function removePayment(req, res) {
  await debts.deletePayment(numericId(req.params.paymentId));
  res.status(204).end();
}
