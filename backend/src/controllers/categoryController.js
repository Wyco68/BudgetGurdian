import * as categories from '../services/categoryService.js';

export async function list(_req, res) {
  res.json(await categories.listCategories());
}
