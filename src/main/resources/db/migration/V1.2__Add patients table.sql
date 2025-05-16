CREATE TABLE IF NOT EXISTS patients
(
    id character varying(8) NOT NULL,
    name character varying(25) COLLATE pg_catalog."default" NOT NULL,
    date_of_birth date NOT NULL,
    disease character varying(25) COLLATE pg_catalog."default" NOT NULL,
    bed_type character varying(10) COLLATE pg_catalog."default" NOT NULL,
    hospital_id character varying(8) NOT NULL,
    CONSTRAINT patients_pkey PRIMARY KEY (id)
);