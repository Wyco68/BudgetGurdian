// Single PrismaClient for the whole process. Prisma manages its own pool;
// instantiating one client per request would exhaust Supabase connections.
import { PrismaClient } from '@prisma/client';

export const prisma = new PrismaClient();
