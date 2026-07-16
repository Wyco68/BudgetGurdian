import { Router } from 'express';
import { validateBody } from '../middleware/validate.js';
import {
  balanceSchema,
  debtPaymentSchema,
  debtSchema,
  debtStatusSchema,
  refillItemSchema,
  settingSchema,
  transactionSchema,
  transferSchema,
} from '../schemas.js';
import * as accounts from '../controllers/accountController.js';
import * as categories from '../controllers/categoryController.js';
import * as transactions from '../controllers/transactionController.js';
import * as transfers from '../controllers/transferController.js';
import * as debts from '../controllers/debtController.js';
import * as refills from '../controllers/refillController.js';
import * as settings from '../controllers/settingController.js';

/** All /api/v1 resource routes. Bodies are zod-validated before controllers run. */
export const apiRouter = Router();

// Accounts — fixed set; only balances change.
apiRouter.get('/accounts', accounts.list);
apiRouter.put('/accounts/:id/balance', validateBody(balanceSchema), accounts.updateBalance);

// Categories — read-only.
apiRouter.get('/categories', categories.list);

// Transactions — the ledger.
apiRouter.get('/transactions', transactions.list);
apiRouter.post('/transactions', validateBody(transactionSchema), transactions.create);
apiRouter.post('/transactions/:id/restore', validateBody(transactionSchema), transactions.restore);
apiRouter.put('/transactions/:id', validateBody(transactionSchema), transactions.update);
apiRouter.delete('/transactions/:id', transactions.remove);

// Transfers — between-account moves, never income/expense.
apiRouter.get('/transfers', transfers.list);
apiRouter.post('/transfers', validateBody(transferSchema), transfers.create);
apiRouter.delete('/transfers/:id', transfers.remove);

// Debts and their partial payments.
apiRouter.get('/debts', debts.list);
apiRouter.post('/debts', validateBody(debtSchema), debts.create);
apiRouter.put('/debts/:id/status', validateBody(debtStatusSchema), debts.updateStatus);
apiRouter.delete('/debts/:id', debts.remove);
apiRouter.get('/debt-payments', debts.listPayments);
apiRouter.post('/debts/:id/payments', validateBody(debtPaymentSchema), debts.createPayment);
apiRouter.delete('/debt-payments/:paymentId', debts.removePayment);

// Refill items — keyed by name.
apiRouter.get('/refill-items', refills.list);
apiRouter.put('/refill-items/:name', validateBody(refillItemSchema), refills.upsert);
apiRouter.delete('/refill-items/:name', refills.remove);

// Settings — key-value.
apiRouter.get('/settings', settings.list);
apiRouter.put('/settings/:key', validateBody(settingSchema), settings.put);
