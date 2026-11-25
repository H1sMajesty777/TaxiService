
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


-- Выполняется один раз в новой базе. Все пассажиры и заказы вымышлены.

INSERT INTO passengers (full_name, phone) VALUES
    ('Анна Миронова', '+70000000001'),
    ('Илья Новиков', '+70000000002'),
    ('Мария Орлова', '+70000000003'),
    ('Олег Климов', '+70000000004'),
    ('Дарья Зотова', '+70000000005');

INSERT INTO ride_orders (passenger_id, pickup_address, dropoff_address, pickup_at, fare, status)
SELECT passengers.id, demo.pickup_address, demo.dropoff_address, demo.pickup_at::timestamp,
       demo.fare, demo.status
FROM (VALUES
    (1, '+70000000001', 'Белорусский вокзал', 'ул. Лесная, 5', '2026-11-23 09:00', 480.00, 'COMPLETED'),
    (2, '+70000000002', 'ул. Арбат, 12', 'Киевский вокзал', '2026-11-23 11:10', 650.00, 'COMPLETED'),
    (3, '+70000000003', 'м. Парк культуры', 'ул. Покровка, 8', '2026-11-24 08:30', 510.00, 'CANCELLED'),
    (4, '+70000000004', 'ул. Тверская, 9', 'Павелецкий вокзал', '2026-11-24 12:10', 720.00, 'COMPLETED'),
    (5, '+70000000005', 'ул. Остоженка, 2', 'м. Сокольники', '2026-11-25 09:10', 640.00, 'IN_PROGRESS'),
    (6, '+70000000001', 'ул. Лесная, 5', 'Казанский вокзал', '2026-11-25 14:00', 580.00, 'CONFIRMED'),
    (7, '+70000000002', 'м. Семёновская', 'ул. Покровка, 8', '2026-11-25 18:30', 550.00, 'NEW'),
    (8, '+70000000003', 'Киевский вокзал', 'ул. Арбат, 12', '2026-11-26 10:40', 620.00, 'CONFIRMED'),
    (9, '+70000000004', 'ул. Тверская, 9', 'м. Парк культуры', '2026-11-26 14:20', 490.00, 'NEW'),
    (10, '+70000000005', 'Павелецкий вокзал', 'ул. Остоженка, 2', '2026-11-27 17:00', 570.00, 'CANCELLED')
) AS demo (demo_order, phone, pickup_address, dropoff_address, pickup_at, fare, status)
JOIN passengers ON passengers.phone = demo.phone
ORDER BY demo.demo_order;
