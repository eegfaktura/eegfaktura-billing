-- Billing's own rows for the M6 read test (LegacyMasterdataEntityReadTests), inserted into the BASE
-- TABLES of the legacy schema that src/test/resources/legacy-base/ builds; the view
-- base.billing_masterdata derives the billing columns from them. Fictional community, people and ids.
-- Runs inside the test transaction (@Sql) and is rolled back.
insert into base.eeg ("tenant", "name", "description", "rcNumber", "area", "gridoperator_code", "gridoperator_name",
                      "communityId", "businessNr", "taxNumber", "vatNumber", "subjecttovat", "street", "streetNumber",
                      "city", "zip", "iban", "owner", "bankName", "phone", "email", "website", "creditor_id")
values ('RC100001', 'EEG Sonnenschein', 'Erneuerbare-Energie-Gemeinschaft Sonnenschein', 'RC100001', 'LOCAL',
        'AT009999', 'Test Operator', 'AT00999900000RC100001000000000', 'FN 123456a', '12 345/6789', 'ATU12345678',
        true, 'Hauptstraße', '1', 'Graz', '8010', 'AT611904300234573201', 'EEG Sonnenschein', 'Testbank',
        '+43 316 000000', 'office@eeg-sonnenschein.test', 'https://eeg-sonnenschein.test', 'AT12ZZZ00000000001');

-- Member tariff (EEG) and two meter tariffs; the consumer tariff has two versions, the view takes the newest.
insert into base.tariff (id, tenant, type, name, "useVat", "vatInPercent", "participantFee", discount, version)
values ('00000000-0000-0000-0000-0000000000e1', 'RC100001', 'EEG', 'Mitgliedsbeitrag', true, 20, 12.5, 0, 1);
insert into base.tariff (id, tenant, type, name, "billingPeriod", "useVat", "vatInPercent", "centPerKWh", "baseFee",
                         discount, "freeKWh", version)
values ('00000000-0000-0000-0000-0000000000c1', 'RC100001', 'VZP', 'Bezug alt', 'monthly', true, 20, 10, 0, 0, 0, 1),
       ('00000000-0000-0000-0000-0000000000c1', 'RC100001', 'VZP', 'Bezug', 'monthly', true, 20, 12.5, 0, 5, 0, 2);
insert into base.tariff (id, tenant, type, name, "billingPeriod", "useVat", "vatInPercent", "centPerKWh",
                         "meteringPointFee", "meteringPointVat", "useMeteringPointFee", version)
values ('00000000-0000-0000-0000-0000000000a1', 'RC100001', 'EZP', 'Einspeisung', 'monthly', false, 0, 8, 3.5, 20,
        true, 1);

insert into base.participant (id, "participantNumber", tenant, firstname, lastname, "titleBefore", "participantSince",
                              "vatNumber", "createdBy", "lastModifiedBy", "tariffId")
values ('00000000-0000-0000-0000-000000000101', '0001', 'RC100001', 'Anna', 'Muster', 'Dr.', '2024-01-15',
        'ATU87654321', 'test', 'test', '00000000-0000-0000-0000-0000000000e1');
insert into base.address (participant_id, type, street, "streetNumber", city, zip)
values ('00000000-0000-0000-0000-000000000101', 'BILLING', 'Gartenweg', '5', 'Graz', '8020'),
       ('00000000-0000-0000-0000-000000000101', 'RESIDENCE', 'Nebenweg', '9', 'Leoben', '8700');
insert into base.bankaccount (participant_id, iban, owner, "bankName", mandate_reference, mandate_date, sepa_direct_debit)
values ('00000000-0000-0000-0000-000000000101', 'AT483200000012345864', 'Anna Muster', 'Raiffeisen Test', 'MR-0001',
        '2024-02-01', 'CORE');
insert into base.contactdetail (participant_id, email, phone)
values ('00000000-0000-0000-0000-000000000101', 'anna.muster@example.test', '+43 660 0000000');
insert into base.meteringpoint (metering_point_id, participant_id, tenant, direction, tariff_id, "equipmentNumber",
                                "equipmentName")
values ('AT0099990000000000000000000000001', '00000000-0000-0000-0000-000000000101', 'RC100001', 'CONSUMPTION',
        '00000000-0000-0000-0000-0000000000c1', 'A-1', 'Haushalt'),
       ('AT0099990000000000000000000000002', '00000000-0000-0000-0000-000000000101', 'RC100001', 'GENERATION',
        '00000000-0000-0000-0000-0000000000a1', 'A-2', 'PV Dach');
