import * as accounts from '../services/accountService.js';

export async function list(_req, res) {
  res.json(await accounts.listAccounts());
}

export async function updateBalance(req, res) {
  res.json(await accounts.updateBalance(req.params.id, req.body.balanceSatang));
}
