-- known-errors #32 (data part): logo and footer images uploaded before 1.2.0 (4af89af) carry no tenant_id, so
-- GET /api/fileData/{id} refuses them. Set it from the billing_config that references the row as header or footer
-- image - only rows without tenant_id, referenced by configs of exactly one community and by nothing else (no
-- document PDF, no custom template, no mail-text image). That community can read the image already through
-- /api/billingConfigs/{id}/logoImage|footerImage. Other rows stay as they are (refused as before). Idempotent: a
-- second run finds no row.
update file_data f
set tenant_id = owner.tenant_id
from (select r.file_data_id, min(r.tenant_id) as tenant_id
      from (select header_image_file_data_id as file_data_id, tenant_id from billing_config
            where header_image_file_data_id is not null
            union all
            select footer_image_file_data_id, tenant_id from billing_config
            where footer_image_file_data_id is not null) r
      group by r.file_data_id
      having count(distinct upper(r.tenant_id)) = 1) owner
where f.id = owner.file_data_id
  and f.tenant_id is null
  and not exists (select 1 from billing_config c where c.custom_template_file_data_id = f.id)
  and not exists (select 1 from billing_document_file d where d.file_data_id = f.id)
  and not exists (select 1 from billing_mail_template_file m where m.file_data_id = f.id);
