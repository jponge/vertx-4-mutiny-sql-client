create table Product
(
    id    serial primary key,
    name  varchar(255)   not null,
    price numeric(19, 2) not null
);
