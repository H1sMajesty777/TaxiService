BEGIN;

CREATE TABLE passengers (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name varchar(100) NOT NULL,
    phone varchar(12) NOT NULL,
    CONSTRAINT passenger_name_length CHECK (char_length(btrim(full_name)) >= 2),
    CONSTRAINT passenger_phone_format CHECK (phone ~ '^[+]7[0-9]{10}$'),
    CONSTRAINT passenger_phone_unique UNIQUE (phone)
);

CREATE TABLE ride_orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    passenger_id bigint NOT NULL,
    pickup_address varchar(150) NOT NULL,
    dropoff_address varchar(150) NOT NULL,
    pickup_at timestamp without time zone NOT NULL,
    fare numeric(9, 2) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'NEW',
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ride_passenger_fk FOREIGN KEY (passenger_id)
        REFERENCES passengers (id) ON DELETE RESTRICT,
    CONSTRAINT ride_addresses_valid CHECK (
        char_length(btrim(pickup_address)) >= 3
        AND char_length(btrim(dropoff_address)) >= 3
        AND lower(btrim(pickup_address)) <> lower(btrim(dropoff_address))
    ),
    CONSTRAINT ride_pickup_at_range CHECK (
        pickup_at >= timestamp '2000-01-01' AND pickup_at < timestamp '2100-01-01'
    ),
    CONSTRAINT ride_positive_fare CHECK (fare > 0 AND fare <= 9999999.99),
    CONSTRAINT ride_status_values CHECK (
        status IN ('NEW', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT ride_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX ride_orders_passenger_id_idx ON ride_orders (passenger_id);

COMMIT;
