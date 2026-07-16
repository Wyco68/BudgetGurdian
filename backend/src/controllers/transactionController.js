import * as transactions from '../services/transactionService.js';
import { numericId } from '../utils/params.js';

export async function list(_req, res) {
  res.json(await transactions.listTransactions());
}

export async function create(req, res) {
  res.status(201).json(await transactions.createTransaction(req.body));
}

export async function restore(req, res) {
  res.status(201).json(await transactions.restoreTransaction(numericId(req.params.id), req.body));
}

export async function update(req, res) {
  res.json(await transactions.updateTransaction(numericId(req.params.id), req.body));
}

export async function remove(req, res) {
  await transactions.deleteTransaction(numericId(req.params.id));
  res.status(204).end();
}
