-- Editable member mail texts (konzept-mail-texte-editierbar.md): one stored version per tenant and
-- mail key, plus the images uploaded for that mail. Additive only; an older image ignores both tables.
create table if not exists billing_mail_template (
    id              uuid      not null,
    tenant_id       varchar   not null,
    mail_key        varchar   not null default 'billing-document',
    subject         varchar   not null,
    body_html       varchar   not null,
    version         integer   not null,
    default_version integer   not null,
    date_created    timestamp(6) not null,
    last_updated    timestamp(6) not null,
    updated_by      varchar,
    primary key (id),
    constraint billing_mail_template_tenant_key_uk unique (tenant_id, mail_key)
);

-- Files belong to (tenant_id, mail_key), not to a stored template: an image can be uploaded before
-- the first save. The bytes live in file_data (with tenant_id set).
create table if not exists billing_mail_template_file (
    id           uuid      not null,
    tenant_id    varchar   not null,
    mail_key     varchar   not null,
    file_data_id uuid      not null,
    kind         varchar   not null,
    size         integer   not null,
    date_created timestamp(6) not null,
    primary key (id),
    constraint billing_mail_template_file_file_data_fk foreign key (file_data_id) references file_data
);
create index if not exists billing_mail_template_file_tenant_key_idx on billing_mail_template_file (tenant_id, mail_key);
