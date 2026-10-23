-- Copied unchanged from eegfaktura-backend/migrations/20250603103206_activeMeterView.up.sql at da3d505a5e6182436deb0ef612ffd803629ea3eb (AGPL-3.0).
-- The legacy base schema for v3's local/E2E stacks and the ITs (m02 T1); applied by docker/postgres-init/40-legacy-base.sh.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- Add new schema named "base"
CREATE SCHEMA IF NOT EXISTS "base";
-- create "eeg" table
CREATE TABLE IF NOT EXISTS "base"."eeg"
(
    "tenant"             character varying     NOT NULL,
    "name"               text                  NOT NULL,
    "description"        character varying(100) NULL,
    "periods"            json                  NULL     DEFAULT '[]',
    "rcNumber"           text                  NOT NULL,
    "area"               text                  NOT NULL,
    "legal"              text                  NOT NULL DEFAULT 'verein',
    "gridoperator_code"  text                  NOT NULL,
    "gridoperator_name"  text                  NOT NULL,
    "communityId"        text                  NOT NULL,
    "businessNr"         text                  NULL,
    "allocationMode"     text                  NOT NULL DEFAULT 'DYNAMIC',
    "settlementInterval" text                  NOT NULL DEFAULT 'MONTHLY',
    "providerBusinessNr" integer               NULL,
    "taxNumber"          text                  NULL,
    "vatNumber"          text                  NULL,
    "subjecttovat"       boolean               NULL,
    "contactPerson"      text                  NULL,
    "createdat"          date                  NOT NULL DEFAULT (now())::date,
    "street"             text                  NOT NULL,
    "streetNumber"       text                  NOT NULL,
    "city"               text                  NOT NULL,
    "zip"                text                  NOT NULL,
    "iban"               text                  NULL,
    "owner"              text                  NULL,
    "sepa"               boolean               NOT NULL DEFAULT false,
    "bankName"           text                  NULL,
    "phone"              text                  NULL,
    "email"              text                  NOT NULL,
    "website"            text                  NULL,
    "online"             boolean               NOT NULL DEFAULT false,
    PRIMARY KEY ("tenant")
);
-- create index "idx_unique_eeg" to table: "eeg"
CREATE UNIQUE INDEX IF NOT EXISTS "idx_unique_eeg" ON "base"."eeg" ("tenant", "name", "rcNumber");
-- create "gridoperators" table
CREATE TABLE IF NOT EXISTS "base"."gridoperators"
(
    "id"   character varying NOT NULL,
    "name" character varying NOT NULL,
    CONSTRAINT "pk_gridoperators" PRIMARY KEY ("id", "name")
);
-- create "notification" table
CREATE TABLE IF NOT EXISTS "base"."notification"
(
    "id"           serial            NOT NULL,
    "tenant"       text              NOT NULL,
    "type"         text              NOT NULL DEFAULT 'MESSAGE',
    "process"      text              NOT NULL DEFAULT 'EDA_PROCESS',
    "notification" json              NOT NULL DEFAULT '{}',
    "date"         timestamp         NOT NULL DEFAULT now(),
    "role"         character varying NOT NULL DEFAULT 'ADMIN',
    PRIMARY KEY ("id")
);
-- create "participant" table
CREATE TABLE IF NOT EXISTS "base"."participant"
(
    "id"                    uuid              NOT NULL DEFAULT public.uuid_generate_v4(),
    "participantNumber"     character varying NULL,
    "tenant"                character varying NOT NULL,
    "firstname"             character varying NOT NULL,
    "lastname"              character varying NOT NULL DEFAULT ''::character varying,
    "role"                  character varying NOT NULL DEFAULT 'EEG_USER'::character varying,
    "businessRole"          character varying NOT NULL DEFAULT 'EEG_PRIVATE'::character varying,
    "titleBefore"           character varying NULL,
    "titleAfter"            character varying NULL,
    "participantSince"      date              NOT NULL DEFAULT now()::date,
    "vatNumber"             character varying NULL,
    "taxNumber"             character varying NULL,
    "companyRegisterNumber" character varying NULL,
    "status"                character varying NOT NULL DEFAULT 'NEW'::character varying,
    "createdBy"             character varying NOT NULL,
    "createdDate"           date              NULL     DEFAULT now()::date,
    "lastModifiedBy"        character varying NOT NULL,
    "lastModifiedDate"      date              NULL     DEFAULT now()::date,
    "version"               integer           NULL     DEFAULT 1,
    "tariffId"              uuid              NULL,
    CONSTRAINT "participantpk" PRIMARY KEY ("id")
);
-- create index "idx_unique_participant_tenant" to table: "participant"
CREATE UNIQUE INDEX IF NOT EXISTS "idx_unique_participant_tenant" ON "base"."participant" ("id", "tenant", "version");
-- create "processhistory" table
CREATE TABLE IF NOT EXISTS "base"."processhistory"
(
    "id"             uuid              NOT NULL DEFAULT public.uuid_generate_v4(),
    "tenant"         text              NOT NULL,
    "conversationId" text              NOT NULL,
    "type"           text              NOT NULL,
    "date"           timestamp         NOT NULL DEFAULT (now()),
    "issuer"         text              NOT NULL,
    "message"        json              NOT NULL DEFAULT '{}',
    "direction"      text              NOT NULL DEFAULT 'OUT',
    "protocol"       character varying NULL,
    PRIMARY KEY ("id")
);

-- create "tariff" table
CREATE TABLE IF NOT EXISTS "base"."tariff"
(
    id                     uuid             DEFAULT public.uuid_generate_v4() NOT NULL,
    tenant                 varchar                                            NOT NULL,
    type                   varchar                                            NOT NULL,
    name                   text                                               NOT NULL,
    "billingPeriod"        text             DEFAULT 'monthly'::text,
    "useVat"               boolean          DEFAULT false,
    "vatSupplementaryText" text             DEFAULT ''::text                  NOT NULL,
    "vatInPercent"         numeric          DEFAULT 0                         NOT NULL,
    "accountNetAmount"     numeric          DEFAULT 0,
    "accountGrossAmount"   numeric          DEFAULT 0,
    "participantFee"       real             DEFAULT 0                         NOT NULL,
    "baseFee"              double precision DEFAULT 0                         NOT NULL,
    "freeKWh"              integer          DEFAULT 0,
    "businessNr"           integer,
    "createdBy"            text,
    "createdDate"          date             DEFAULT now()::date,
    "lastModifiedDate"     date             DEFAULT now()::date,
    version                integer                                            NOT NULL,
    "centPerKWh"           double precision DEFAULT 0,
    discount               integer          DEFAULT 0,
    status                 text             DEFAULT 'ACTIVE'::text            NOT NULL,
    "inactiveSince"        date,
    "meteringPointFee"     double precision,
    "meteringPointVat"     integer,
    "useMeteringPointFee"  boolean          DEFAULT false                     NOT NULL,
    CONSTRAINT tariffpk PRIMARY KEY (id, version)
);
-- create index "idx_tariff" to table: "tariff"
CREATE UNIQUE INDEX IF NOT EXISTS "idx_tariff" ON "base"."tariff" ("id", "tenant", "name", "type", "version");

-- create "address" table
CREATE TABLE IF NOT EXISTS "base"."address"
(
    "id"             uuid NOT NULL DEFAULT public.uuid_generate_v4(),
    "participant_id" uuid NOT NULL,
    "type"           text NOT NULL DEFAULT 'RESIDENCE',
    "street"         text NULL,
    "streetNumber"   text NULL,
    "city"           text NULL,
    "zip"            text NULL,
    CONSTRAINT "addresspk" PRIMARY KEY ("id"),
    CONSTRAINT "fk_participantaddress" FOREIGN KEY ("participant_id") REFERENCES "base"."participant" ("id") ON UPDATE NO ACTION ON DELETE CASCADE
);
-- create "bankaccount" table
CREATE TABLE IF NOT EXISTS "base"."bankaccount"
(
    "id"             uuid NOT NULL DEFAULT public.uuid_generate_v4(),
    "participant_id" uuid NOT NULL,
    "iban"           text NULL,
    "owner"          text NULL,
    "bankName"       text NULL,
    CONSTRAINT "bankaccountpk" PRIMARY KEY ("id"),
    CONSTRAINT "fk_participantbankaccount" FOREIGN KEY ("participant_id") REFERENCES "base"."participant" ("id") ON UPDATE NO ACTION ON DELETE CASCADE
);
-- create "contactdetail" table
CREATE TABLE IF NOT EXISTS "base"."contactdetail"
(
    "id"             uuid NOT NULL DEFAULT public.uuid_generate_v4(),
    "participant_id" uuid NOT NULL,
    "email"          text NULL,
    "phone"          text NULL,
    CONSTRAINT "contactdetailspk" PRIMARY KEY ("id"),
    CONSTRAINT "fk_participantdetail" FOREIGN KEY ("participant_id") REFERENCES "base"."participant" ("id") ON UPDATE NO ACTION ON DELETE CASCADE
);
-- create "meteringpoint" table
CREATE TABLE IF NOT EXISTS "base"."meteringpoint"
(
    "metering_point_id"  text              NOT NULL,
    "consent_id"         text              NULL,
    "participant_id"     uuid              NOT NULL,
    "tenant"             text              NOT NULL,
    "grid_operator_name" character varying NULL,
    "grid_operator_id"   character varying NULL,
    "allocation_factor"  double precision  NULL,
    "transformer"        text              NULL,
    "direction"          text              NOT NULL DEFAULT 'CONSUMPTION',
    "status"             text              NOT NULL DEFAULT 'INIT',
    "process_state"      text              NOT NULL DEFAULT 'NEW',
    "statusCode"         integer           NULL,
    "tariff_id"          uuid              NULL,
    "inverterid"         text              NULL,
    "equipmentNumber"    text              NULL,
    "equipmentName"      text              NULL,
    "street"             text              NULL,
    "streetNumber"       text              NULL,
    "city"               text              NULL,
    "zip"                text              NULL,
    "registeredSince"    date              NOT NULL DEFAULT (now())::date,
    "modifiedAt"         timestamp         NOT NULL DEFAULT (now())::date,
    "modifiedBy"         text              NULL,
    "activesince"        date              NULL,
    "inactivesince"      date              NULL,
    "active"             integer           NOT NULL DEFAULT 1,
    "flag"               integer           NOT NULL DEFAULT 1,
    CONSTRAINT "meteringpointpk" PRIMARY KEY ("metering_point_id", "tenant", "participant_id"),
    CONSTRAINT "fk_participantmeteringpoint" FOREIGN KEY ("participant_id") REFERENCES "base"."participant" ("id") ON UPDATE NO ACTION ON DELETE CASCADE
);
-- create index "idx_unique_meteringpoint_active" to table: "meteringpoint"
CREATE UNIQUE INDEX IF NOT EXISTS "idx_unique_meteringpoint_active" ON "base"."meteringpoint" ("metering_point_id", "tenant", "flag") WHERE (flag = 1);
-- create "metering_partition_factor" table
CREATE TABLE IF NOT EXISTS "base"."metering_partition_factor"
(
    "metering_point_id" text              NOT NULL,
    "version"           serial            NOT NULL,
    "participant_id"    uuid              NOT NULL,
    "tenant"            text              NOT NULL,
    "partFact"          integer           NOT NULL,
    "createdAt"         date              NOT NULL DEFAULT (now())::date,
    "createdBy"         character varying NOT NULL,
    CONSTRAINT "meteringpointpartitionpk" PRIMARY KEY ("metering_point_id", "version"),
    CONSTRAINT "fk_meteringpointpartition" FOREIGN KEY ("metering_point_id", "tenant", "participant_id") REFERENCES "base"."meteringpoint" ("metering_point_id", "tenant", "participant_id") ON UPDATE CASCADE ON DELETE CASCADE
);
