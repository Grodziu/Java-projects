-- Enrich pre-upgrade command replies without changing their bid or deadline.
CREATE FUNCTION bidroom_complete_snapshot(value JSONB) RETURNS JSONB LANGUAGE plpgsql AS $$
DECLARE result JSONB; pair RECORD; number BIGINT;
BEGIN
 IF jsonb_typeof(value)='object' THEN
  result='{}'::jsonb;
  FOR pair IN SELECT * FROM jsonb_each(value) LOOP
   result=result||jsonb_build_object(pair.key,bidroom_complete_snapshot(pair.value));
  END LOOP;
  IF value ? 'auctionId' AND value ? 'title' THEN
   SELECT auction_number INTO number FROM auction WHERE id::text=value->>'auctionId';
   IF number IS NOT NULL THEN result=result||jsonb_build_object('auctionNumber',number,'photoVersion',coalesce(value->'photoVersion','0'::jsonb)); END IF;
  END IF;
  RETURN result;
 ELSIF jsonb_typeof(value)='array' THEN
  SELECT coalesce(jsonb_agg(bidroom_complete_snapshot(x)), '[]'::jsonb) INTO result FROM jsonb_array_elements(value) x;
  RETURN result;
 END IF;
 RETURN value;
END $$;
UPDATE command_result SET result=bidroom_complete_snapshot(result);
DROP FUNCTION bidroom_complete_snapshot(JSONB);
