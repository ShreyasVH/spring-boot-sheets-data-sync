CREATE TABLE IF NOT EXISTS hospitals
(
    id character varying(8) NOT NULL,
    name character varying(25) COLLATE pg_catalog."default" NOT NULL,
    location character varying(25) COLLATE pg_catalog."default" NOT NULL,
    general_bed_count smallint NOT NULL,
    icu_bed_count smallint NOT NULL,
    CONSTRAINT hospitals_pkey PRIMARY KEY (id)
);