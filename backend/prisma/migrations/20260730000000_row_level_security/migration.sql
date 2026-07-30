-- Row Level Security baseline.
--
-- Every table in `public` must (a) have RLS enabled and (b) carry exactly one
-- policy, `backend_all`, granting full access to the `budget_backend` role the
-- API connects as. Nothing else may read the data: `anon` and `authenticated`
-- hold table grants (Supabase's defaults) but no policy, so RLS denies them —
-- the anon key that ships in client code cannot reach a single row.
--
-- Until this migration, `public.bill` had neither. It was protected only by
-- owner-only table privileges, one GRANT away from being world-writable.
--
-- Written as an idempotent loop rather than plain DDL for two reasons:
--   * tables that are already correct are skipped, so this is safe to re-run
--     and safe on tables owned by `postgres` (which `budget_backend` may not
--     ALTER — it never needs to, since those are already configured);
--   * any table added later and missed by hand is fixed the next time this
--     runs, instead of silently shipping without RLS.
DO $$
DECLARE
  target record;
BEGIN
  FOR target IN
    SELECT c.relname
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'r'
      AND NOT c.relrowsecurity
  LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', target.relname);
    RAISE NOTICE 'Enabled RLS on public.%', target.relname;
  END LOOP;

  FOR target IN
    SELECT c.relname
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'r'
      AND NOT EXISTS (
        SELECT 1 FROM pg_policies p
        WHERE p.schemaname = 'public'
          AND p.tablename = c.relname
          AND p.policyname = 'backend_all'
      )
  LOOP
    EXECUTE format(
      'CREATE POLICY backend_all ON public.%I FOR ALL TO budget_backend USING (true) WITH CHECK (true)',
      target.relname
    );
    RAISE NOTICE 'Created backend_all policy on public.%', target.relname;
  END LOOP;
END
$$;
