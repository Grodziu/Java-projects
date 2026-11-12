-- Preserve existing accounts, bids, audit and idempotency while replacing user UUID keys.
ALTER TABLE app_user ADD COLUMN numeric_id BIGINT GENERATED ALWAYS AS IDENTITY;
DO $$
DECLARE item TEXT[]; items TEXT[][] := ARRAY[
 ARRAY['auction','owner_id'], ARRAY['auction','leader_id'], ARRAY['bid','bidder_id'],
 ARRAY['auction_event','actor_id'], ARRAY['command_result','user_id'],
 ARRAY['security_audit','actor_id'], ARRAY['security_audit','target_user_id'],
 ARRAY['diagnostic_log','reported_by_user_id']];
BEGIN
 FOREACH item SLICE 1 IN ARRAY items LOOP
  EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I',item[1],item[1]||'_'||item[2]||'_fkey');
  EXECUTE format('ALTER TABLE %I ADD COLUMN migration_user_number BIGINT',item[1]);
  EXECUTE format('UPDATE %I t SET migration_user_number=u.numeric_id FROM app_user u WHERE t.%I=u.id',item[1],item[2]);
  EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE BIGINT USING migration_user_number',item[1],item[2]);
  EXECUTE format('ALTER TABLE %I DROP COLUMN migration_user_number',item[1]);
 END LOOP;
END $$;
ALTER TABLE app_user DROP CONSTRAINT app_user_pkey;
ALTER TABLE app_user RENAME COLUMN id TO legacy_uuid;
ALTER TABLE app_user RENAME COLUMN numeric_id TO id;
ALTER TABLE app_user ADD PRIMARY KEY(id);
ALTER TABLE app_user ADD UNIQUE(legacy_uuid);
DO $$
DECLARE item TEXT[]; items TEXT[][] := ARRAY[
 ARRAY['auction','owner_id'], ARRAY['auction','leader_id'], ARRAY['bid','bidder_id'],
 ARRAY['auction_event','actor_id'], ARRAY['command_result','user_id'],
 ARRAY['security_audit','actor_id'], ARRAY['security_audit','target_user_id'],
 ARRAY['diagnostic_log','reported_by_user_id']];
BEGIN
 FOREACH item SLICE 1 IN ARRAY items LOOP
  EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY(%I) REFERENCES app_user(id)',item[1],item[1]||'_'||item[2]||'_fkey',item[2]);
 END LOOP;
END $$;

-- Old pending bids can still recover the same business result in protocol v2.
CREATE FUNCTION bidroom_migrate_result(value JSONB, field_name TEXT DEFAULT '') RETURNS JSONB
LANGUAGE plpgsql AS $$
DECLARE result JSONB; pair RECORD; number BIGINT;
BEGIN
 IF jsonb_typeof(value)='object' THEN
  result='{}'::jsonb;
  FOR pair IN SELECT * FROM jsonb_each(value) LOOP
   result=result||jsonb_build_object(pair.key,bidroom_migrate_result(pair.value,pair.key));
  END LOOP;
  RETURN result;
 ELSIF jsonb_typeof(value)='array' THEN
  SELECT coalesce(jsonb_agg(bidroom_migrate_result(x)), '[]'::jsonb) INTO result FROM jsonb_array_elements(value) x;
  RETURN result;
 ELSIF jsonb_typeof(value)='string' AND field_name IN ('id','userId','ownerId','leaderId','bidderId') THEN
  SELECT id INTO number FROM app_user WHERE legacy_uuid::text=value#>>'{}';
  IF number IS NOT NULL THEN RETURN to_jsonb(number); END IF;
 END IF;
 RETURN value;
END $$;
UPDATE command_result SET result=jsonb_set(bidroom_migrate_result(result),'{protocolVersion}','2'::jsonb);
DROP FUNCTION bidroom_migrate_result(JSONB,TEXT);

ALTER TABLE auction ADD COLUMN auction_number BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE;
ALTER TABLE auction ADD COLUMN photo_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE auction_photo (
 auction_id UUID PRIMARY KEY REFERENCES auction(id), version BIGINT NOT NULL,
 content BYTEA NOT NULL CHECK(octet_length(content)<=2097152), sha256 TEXT NOT NULL,
 width INTEGER NOT NULL, height INTEGER NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE auction_report (
 auction_id UUID PRIMARY KEY REFERENCES auction(id), filename TEXT NOT NULL UNIQUE,
 sha256 TEXT NOT NULL, bytes BIGINT NOT NULL, generated_at TIMESTAMPTZ NOT NULL
);
