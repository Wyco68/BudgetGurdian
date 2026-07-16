import * as settings from '../services/settingsService.js';

export async function list(_req, res) {
  res.json(await settings.listSettings());
}

export async function put(req, res) {
  res.json(await settings.putSetting(req.params.key, req.body.value));
}
