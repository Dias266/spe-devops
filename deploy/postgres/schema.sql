CREATE TABLE IF NOT EXISTS service_record (
    id          INTEGER PRIMARY KEY CHECK (id > 0),
    vehicle_id  TEXT    NOT NULL,
    part        TEXT    NOT NULL,
    technician  TEXT    NOT NULL,
    cost        INTEGER NOT NULL CHECK (cost >= 0),
    endorser_id TEXT,
    approved    BOOLEAN,
    decided_at  TIMESTAMPTZ,
    -- the three endorsement columns are either all empty or all filled
    CHECK ((endorser_id IS NULL) = (approved IS NULL)
       AND (approved   IS NULL) = (decided_at IS NULL))
);

-- The database also enforces "a record is decided exactly once".
CREATE OR REPLACE FUNCTION forbid_redecide() RETURNS trigger AS $$
BEGIN
    IF OLD.decided_at IS NOT NULL THEN
        RAISE EXCEPTION 'service record % was already decided', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS service_record_decided_once ON service_record;
CREATE TRIGGER service_record_decided_once
    BEFORE UPDATE ON service_record
    FOR EACH ROW EXECUTE FUNCTION forbid_redecide();

-- Maintenance Prediction context: one row per Weka prediction made by ML-model/main.py.
CREATE TABLE IF NOT EXISTS maintenance_prediction (
    id              SERIAL       PRIMARY KEY,
    instance_number INTEGER      NOT NULL,
    instance_data   TEXT         NOT NULL,
    raw_prediction  TEXT         NOT NULL,
    confidence      NUMERIC(5,1) NOT NULL CHECK (confidence BETWEEN 0 AND 100),
    decision        TEXT         NOT NULL,
    predicted_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
