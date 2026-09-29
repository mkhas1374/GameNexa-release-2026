BEGIN;
UPDATE configuration_revisions
SET settings = settings || jsonb_build_object(
  'pricing', COALESCE(settings->'pricing','{}'::jsonb) || jsonb_build_object(
    'reservations', COALESCE(settings->'pricing'->'reservations','{}'::jsonb) || jsonb_build_object(
      'fullHall', jsonb_build_object('09-12',2500000,'15-18',3200000,'18-24',6300000,'15-24',8500000),
      'exclusiveFullDay',15000000
    )
  ),
  'policies', COALESCE(settings->'policies','{}'::jsonb) || jsonb_build_object(
    'reservation', COALESCE(settings->'policies'->'reservation','{}'::jsonb) || jsonb_build_object('vipMultiplier',1.5,'depositPercent',30)
  )
)
WHERE NOT (settings->'pricing' ? 'reservations') OR NOT (settings->'policies' ? 'reservation');
COMMIT;
