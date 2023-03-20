create table single_reference
(
    ID   SERIAL PRIMARY KEY
);

create table dummy_entity
(
    ID   SERIAL PRIMARY KEY,
    NAME VARCHAR(30),
    SINGLE_REFERENCE INTEGER
);

