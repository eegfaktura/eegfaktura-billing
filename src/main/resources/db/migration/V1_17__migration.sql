-- Uploads are drafts until a saved version uses them (konzept-mail-texte-editierbar.md, section H,
-- review 22.10.2026). Additive; V1_16 already ran on Dev and stays unchanged.
alter table if exists billing_mail_template_file add column if not exists committed boolean not null default false;

-- files of versions saved before this migration (Dev only, nothing released) stay part of them
update billing_mail_template_file f
set committed = true
where exists (select 1
              from billing_mail_template t
              where t.tenant_id = f.tenant_id
                and t.mail_key = f.mail_key
                and position('mailfile:' || f.id::text in t.body_html) > 0);
