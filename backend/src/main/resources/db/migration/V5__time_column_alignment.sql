DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='public' AND table_name='api_key'
               AND column_name='expired_at' AND data_type='timestamp with time zone') THEN
    ALTER TABLE api_key ALTER COLUMN expired_at TYPE TIMESTAMP WITHOUT TIME ZONE
      USING expired_at::TIMESTAMP;
  END IF;
END $$;
