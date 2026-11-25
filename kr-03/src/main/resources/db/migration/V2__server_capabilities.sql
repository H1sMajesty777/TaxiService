CREATE TABLE tariffs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(80) NOT NULL UNIQUE,
    description varchar(300) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

INSERT INTO tariffs (id, name, description) VALUES
    ('00000000-0000-4000-a000-000000000001', 'Стандарт', 'Обычная поездка по городу'),
    ('00000000-0000-4000-a000-000000000002', 'Комфорт', 'Поездка с повышенными требованиями к комфорту'),
    ('00000000-0000-4000-a000-000000000003', 'Минивэн', 'Поездка для группы или крупного багажа');

ALTER TABLE ride_orders
    ADD COLUMN public_id uuid NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN external_code varchar(40),
    ADD COLUMN tariff_id uuid NOT NULL DEFAULT '00000000-0000-4000-a000-000000000001' REFERENCES tariffs(id),
    ADD COLUMN child_seat boolean NOT NULL DEFAULT false,
    ADD COLUMN archived boolean NOT NULL DEFAULT false,
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT current_timestamp,
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT current_timestamp;
UPDATE ride_orders SET external_code = 'TX-' || id;
ALTER TABLE ride_orders
    ALTER COLUMN external_code SET NOT NULL,
    ADD CONSTRAINT ride_orders_public_id_unique UNIQUE (public_id),
    ADD CONSTRAINT ride_orders_external_code_unique UNIQUE (external_code);

CREATE FUNCTION set_ride_server_fields() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
    NEW.updated_at = current_timestamp;
    IF NEW.external_code IS NULL THEN
        NEW.external_code = 'TX-' || NEW.id;
    END IF;
    RETURN NEW;
END
$body$;
CREATE TRIGGER ride_server_fields BEFORE INSERT OR UPDATE ON ride_orders
    FOR EACH ROW EXECUTE FUNCTION set_ride_server_fields();

CREATE INDEX ride_orders_tariff_idx ON ride_orders(tariff_id);
CREATE INDEX ride_orders_archived_pickup_idx ON ride_orders(archived, pickup_at, id);
CREATE INDEX ride_orders_status_idx ON ride_orders(status);
CREATE INDEX passengers_normalized_name_idx ON passengers(lower(full_name));

CREATE TABLE ride_tags (
    ride_id bigint NOT NULL REFERENCES ride_orders(id) ON DELETE CASCADE,
    tag varchar(40) NOT NULL,
    PRIMARY KEY (ride_id, tag)
);
CREATE INDEX ride_tags_tag_idx ON ride_tags(tag);

CREATE TABLE roles (code varchar(20) PRIMARY KEY);
INSERT INTO roles VALUES ('VIEWER'), ('DISPATCHER'), ('ADMIN');

CREATE TABLE user_accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email varchar(120) NOT NULL UNIQUE,
    name varchar(100) NOT NULL,
    role varchar(20) NOT NULL REFERENCES roles(code),
    password_hash varchar(72) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    must_change_password boolean NOT NULL DEFAULT true,
    token_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE refresh_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id uuid NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    client varchar(12) NOT NULL CHECK (client IN ('WEB', 'NATIVE')),
    token_version bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL
);
CREATE INDEX refresh_sessions_account_idx ON refresh_sessions(account_id);

CREATE TABLE ride_notes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id bigint NOT NULL REFERENCES ride_orders(id) ON DELETE CASCADE,
    author_id uuid NOT NULL REFERENCES user_accounts(id),
    body varchar(1500) NOT NULL CHECK (length(trim(body)) > 0),
    pinned boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT current_timestamp,
    updated_at timestamp with time zone NOT NULL DEFAULT current_timestamp
);
CREATE INDEX ride_notes_ride_idx ON ride_notes(ride_id);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid REFERENCES user_accounts(id),
    event_type varchar(40) NOT NULL,
    entity_type varchar(25) NOT NULL,
    entity_id uuid,
    occurred_at timestamp with time zone NOT NULL DEFAULT current_timestamp,
    description varchar(300) NOT NULL,
    correlation_id varchar(64) NOT NULL
);
CREATE INDEX audit_time_idx ON audit_events(occurred_at);

UPDATE ride_orders SET tariff_id = '00000000-0000-4000-a000-000000000002', child_seat = true
WHERE external_code IN ('TX-2', 'TX-6') AND passenger_id IN
    (SELECT id FROM passengers WHERE phone IN ('+70000000001', '+70000000002'));
UPDATE ride_orders SET tariff_id = '00000000-0000-4000-a000-000000000003'
WHERE external_code = 'TX-4' AND passenger_id IN
    (SELECT id FROM passengers WHERE phone = '+70000000004');
INSERT INTO ride_tags(ride_id, tag)
SELECT id, 'вокзал' FROM ride_orders WHERE external_code IN ('TX-1', 'TX-4', 'TX-6') AND passenger_id IN
    (SELECT id FROM passengers WHERE phone IN ('+70000000001', '+70000000004'));
