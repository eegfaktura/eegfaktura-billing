
# participantAmounts laden
curl -v -H 'Tenant: <Tenant Id einfuegen>' -H 'Authorization: <Bearer Token einfuegen>' http://localhost:8080/api/billingRuns/<Billing-Run Id einfuegen>/participantAmounts

# XLSX Exportieren
curl -L -O -v -H 'Tenant: <Tenant Id einfuegen>' -H 'Authorization: <Bearer Token einfuegen>' http://localhost:8080/api/billingRuns/<Billing-Run Id einfuegen>/billingDocuments/xlsx

# Mails senden
curl -v -H 'Tenant: <Tenant Id einfuegen>' -H 'Authorization: <Bearer Token einfuegen>' http://localhost:8080/api/billingRuns/<Billing-Run Id einfuegen>/billingDocuments/sendmail

# ZIP Exportieren
curl -L -O -v -H 'Tenant: RC100892' -H 'Authorization: <Bearer token hier einfuegen>' http://localhost:8080/api/billingRuns/<Billing-Run Id einfuegen>/billingDocuments/archive

# Abrechnung durchfuehren
curl -X 'POST' \
  'http://localhost:8080/api/billing' \
  -H 'accept: application/json' \
  -H 'Tenant: <Tenant-Id einfuegen>' \
  -H 'Authorization: <Bearer Token einfuegen>' \
  -H 'Content-Type: application/json' \
  -d '{
  "clearingPeriodType": "MONTHLY",
  "clearingPeriodIdentifier": "2024-MO-06",
  "tenantId": "<Tenant-Id einfuegen>",
  "clearingDocumentDate": "2024-06-09",
  "allocations": [
    {
      "meteringPoint": "ERZ1234000000000089200011",
      "allocationKWh": 62.00
    },
    {
      "meteringPoint": "VERB1234000000000089200012",
      "allocationKWh": 46.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200013",
      "allocationKWh": 403.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200021",
      "allocationKWh": 387.00
    },
    {
      "meteringPoint": "VERB1234000000000089200022",
      "allocationKWh": 287.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200031",
      "allocationKWh": 397.00
    },
    {
      "meteringPoint": "VERB1234000000000089200032",
      "allocationKWh": 58.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200041",
      "allocationKWh": 368.00
    },
    {
      "meteringPoint": "VERB1234000000000089200051",
      "allocationKWh": 215.00
    },
    {
      "meteringPoint": "VERB1234000000000089200052",
      "allocationKWh": 255.00
    },
    {
      "meteringPoint": "VERB1234000000000089200053",
      "allocationKWh": 141.00
    },
    {
      "meteringPoint": "VERB1234000000000089200061",
      "allocationKWh": 17.00
    },
    {
      "meteringPoint": "VERB1234000000000089200062",
      "allocationKWh": 289.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200063",
      "allocationKWh": 295.00
    },
    {
      "meteringPoint": "VERB1234000000000089200071",
      "allocationKWh": 216.00
    },
    {
      "meteringPoint": "VERB1234000000000089200072",
      "allocationKWh": 483.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200081",
      "allocationKWh": 389.00
    },
    {
      "meteringPoint": "VERB1234000000000089200091",
      "allocationKWh": 66.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200101",
      "allocationKWh": 194.00
    },
    {
      "meteringPoint": "VERB1234000000000089200111",
      "allocationKWh": 256.00
    },
    {
      "meteringPoint": "VERB1234000000000089200121",
      "allocationKWh": 190.00
    },
    {
      "meteringPoint": "VERB1234000000000089200122",
      "allocationKWh": 442.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200131",
      "allocationKWh": 472.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200132",
      "allocationKWh": 431.00
    },
    {
      "meteringPoint": "VERB1234000000000089200141",
      "allocationKWh": 30.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200142",
      "allocationKWh": 440.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200151",
      "allocationKWh": 497.00
    },
    {
      "meteringPoint": "VERB1234000000000089200161",
      "allocationKWh": 400.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200162",
      "allocationKWh": 75.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200171",
      "allocationKWh": 331.00
    },
    {
      "meteringPoint": "VERB1234000000000089200172",
      "allocationKWh": 107.00
    },
    {
      "meteringPoint": "VERB1234000000000089200181",
      "allocationKWh": 76.00
    },
    {
      "meteringPoint": "VERB1234000000000089200182",
      "allocationKWh": 384.00
    },
    {
      "meteringPoint": "VERB1234000000000089200191",
      "allocationKWh": 19.00
    },
    {
      "meteringPoint": "VERB1234000000000089200192",
      "allocationKWh": 317.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200201",
      "allocationKWh": 267.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200202",
      "allocationKWh": 350.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200211",
      "allocationKWh": 404.00
    },
    {
      "meteringPoint": "VERB1234000000000089200212",
      "allocationKWh": 51.00
    },
    {
      "meteringPoint": "VERB1234000000000089200221",
      "allocationKWh": 322.00
    },
    {
      "meteringPoint": "VERB1234000000000089200222",
      "allocationKWh": 418.00
    },
    {
      "meteringPoint": "VERB1234000000000089200223",
      "allocationKWh": 187.00
    },
    {
      "meteringPoint": "VERB1234000000000089200231",
      "allocationKWh": 376.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200241",
      "allocationKWh": 426.00
    },
    {
      "meteringPoint": "VERB1234000000000089200251",
      "allocationKWh": 103.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200252",
      "allocationKWh": 101.00
    },
    {
      "meteringPoint": "VERB1234000000000089200253",
      "allocationKWh": 496.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200261",
      "allocationKWh": 108.00
    },
    {
      "meteringPoint": "VERB1234000000000089200262",
      "allocationKWh": 233.00
    },
    {
      "meteringPoint": "VERB1234000000000089200271",
      "allocationKWh": 225.00
    },
    {
      "meteringPoint": "VERB1234000000000089200272",
      "allocationKWh": 251.00
    },
    {
      "meteringPoint": "VERB1234000000000089200273",
      "allocationKWh": 13.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200281",
      "allocationKWh": 301.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200282",
      "allocationKWh": 37.00
    },
    {
      "meteringPoint": "VERB1234000000000089200291",
      "allocationKWh": 16.00
    },
    {
      "meteringPoint": "VERB1234000000000089200301",
      "allocationKWh": 320.00
    },
    {
      "meteringPoint": "VERB1234000000000089200302",
      "allocationKWh": 394.00
    },
    {
      "meteringPoint": "VERB1234000000000089200311",
      "allocationKWh": 228.00
    },
    {
      "meteringPoint": "VERB1234000000000089200321",
      "allocationKWh": 382.00
    },
    {
      "meteringPoint": "VERB1234000000000089200331",
      "allocationKWh": 227.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200332",
      "allocationKWh": 333.00
    },
    {
      "meteringPoint": "VERB1234000000000089200333",
      "allocationKWh": 422.00
    },
    {
      "meteringPoint": "VERB1234000000000089200341",
      "allocationKWh": 192.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200342",
      "allocationKWh": 349.00
    },
    {
      "meteringPoint": "VERB1234000000000089200343",
      "allocationKWh": 155.00
    },
    {
      "meteringPoint": "VERB1234000000000089200351",
      "allocationKWh": 445.00
    },
    {
      "meteringPoint": "VERB1234000000000089200361",
      "allocationKWh": 124.00
    },
    {
      "meteringPoint": "VERB1234000000000089200371",
      "allocationKWh": 399.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200381",
      "allocationKWh": 103.00
    },
    {
      "meteringPoint": "VERB1234000000000089200382",
      "allocationKWh": 383.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200391",
      "allocationKWh": 404.00
    },
    {
      "meteringPoint": "VERB1234000000000089200401",
      "allocationKWh": 24.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200402",
      "allocationKWh": 119.00
    },
    {
      "meteringPoint": "VERB1234000000000089200411",
      "allocationKWh": 65.00
    },
    {
      "meteringPoint": "VERB1234000000000089200412",
      "allocationKWh": 182.00
    },
    {
      "meteringPoint": "VERB1234000000000089200421",
      "allocationKWh": 451.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200422",
      "allocationKWh": 216.00
    },
    {
      "meteringPoint": "VERB1234000000000089200431",
      "allocationKWh": 474.00
    },
    {
      "meteringPoint": "VERB1234000000000089200441",
      "allocationKWh": 423.00
    },
    {
      "meteringPoint": "VERB1234000000000089200451",
      "allocationKWh": 260.00
    },
    {
      "meteringPoint": "VERB1234000000000089200461",
      "allocationKWh": 477.00
    },
    {
      "meteringPoint": "VERB1234000000000089200462",
      "allocationKWh": 372.00
    },
    {
      "meteringPoint": "VERB1234000000000089200471",
      "allocationKWh": 241.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200481",
      "allocationKWh": 157.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200491",
      "allocationKWh": 34.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200501",
      "allocationKWh": 494.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200502",
      "allocationKWh": 264.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200511",
      "allocationKWh": 32.00
    },
    {
      "meteringPoint": "VERB1234000000000089200512",
      "allocationKWh": 481.00
    },
    {
      "meteringPoint": "VERB1234000000000089200521",
      "allocationKWh": 419.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200531",
      "allocationKWh": 175.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200541",
      "allocationKWh": 485.00
    },
    {
      "meteringPoint": "VERB1234000000000089200551",
      "allocationKWh": 299.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200561",
      "allocationKWh": 217.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200571",
      "allocationKWh": 265.00
    },
    {
      "meteringPoint": "VERB1234000000000089200572",
      "allocationKWh": 372.00
    },
    {
      "meteringPoint": "VERB1234000000000089200573",
      "allocationKWh": 469.00
    },
    {
      "meteringPoint": "VERB1234000000000089200581",
      "allocationKWh": 272.00
    },
    {
      "meteringPoint": "VERB1234000000000089200591",
      "allocationKWh": 296.00
    },
    {
      "meteringPoint": "VERB1234000000000089200601",
      "allocationKWh": 478.00
    },
    {
      "meteringPoint": "VERB1234000000000089200611",
      "allocationKWh": 241.00
    },
    {
      "meteringPoint": "VERB1234000000000089200612",
      "allocationKWh": 441.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200621",
      "allocationKWh": 184.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200631",
      "allocationKWh": 56.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200632",
      "allocationKWh": 34.00
    },
    {
      "meteringPoint": "VERB1234000000000089200633",
      "allocationKWh": 26.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200641",
      "allocationKWh": 199.00
    },
    {
      "meteringPoint": "VERB1234000000000089200642",
      "allocationKWh": 306.00
    },
    {
      "meteringPoint": "VERB1234000000000089200643",
      "allocationKWh": 112.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200651",
      "allocationKWh": 163.00
    },
    {
      "meteringPoint": "VERB1234000000000089200652",
      "allocationKWh": 162.00
    },
    {
      "meteringPoint": "VERB1234000000000089200661",
      "allocationKWh": 232.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200671",
      "allocationKWh": 26.00
    },
    {
      "meteringPoint": "VERB1234000000000089200672",
      "allocationKWh": 210.00
    },
    {
      "meteringPoint": "VERB1234000000000089200681",
      "allocationKWh": 175.00
    },
    {
      "meteringPoint": "VERB1234000000000089200682",
      "allocationKWh": 384.00
    },
    {
      "meteringPoint": "VERB1234000000000089200691",
      "allocationKWh": 448.00
    },
    {
      "meteringPoint": "VERB1234000000000089200692",
      "allocationKWh": 88.00
    },
    {
      "meteringPoint": "VERB1234000000000089200701",
      "allocationKWh": 316.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200711",
      "allocationKWh": 461.00
    },
    {
      "meteringPoint": "VERB1234000000000089200721",
      "allocationKWh": 83.00
    },
    {
      "meteringPoint": "VERB1234000000000089200722",
      "allocationKWh": 290.00
    },
    {
      "meteringPoint": "VERB1234000000000089200731",
      "allocationKWh": 247.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200732",
      "allocationKWh": 73.00
    },
    {
      "meteringPoint": "VERB1234000000000089200741",
      "allocationKWh": 375.00
    },
    {
      "meteringPoint": "VERB1234000000000089200742",
      "allocationKWh": 41.00
    },
    {
      "meteringPoint": "VERB1234000000000089200751",
      "allocationKWh": 461.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200761",
      "allocationKWh": 273.00
    },
    {
      "meteringPoint": "VERB1234000000000089200762",
      "allocationKWh": 395.00
    },
    {
      "meteringPoint": "VERB1234000000000089200763",
      "allocationKWh": 268.00
    },
    {
      "meteringPoint": "VERB1234000000000089200771",
      "allocationKWh": 229.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200781",
      "allocationKWh": 133.00
    },
    {
      "meteringPoint": "VERB1234000000000089200782",
      "allocationKWh": 220.00
    },
    {
      "meteringPoint": "VERB1234000000000089200783",
      "allocationKWh": 82.00
    },
    {
      "meteringPoint": "VERB1234000000000089200791",
      "allocationKWh": 310.00
    },
    {
      "meteringPoint": "VERB1234000000000089200792",
      "allocationKWh": 478.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200801",
      "allocationKWh": 445.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200802",
      "allocationKWh": 477.00
    },
    {
      "meteringPoint": "VERB1234000000000089200811",
      "allocationKWh": 301.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200812",
      "allocationKWh": 221.00
    },
    {
      "meteringPoint": "VERB1234000000000089200821",
      "allocationKWh": 318.00
    },
    {
      "meteringPoint": "VERB1234000000000089200831",
      "allocationKWh": 352.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200832",
      "allocationKWh": 127.00
    },
    {
      "meteringPoint": "VERB1234000000000089200841",
      "allocationKWh": 192.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200851",
      "allocationKWh": 164.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200852",
      "allocationKWh": 481.00
    },
    {
      "meteringPoint": "VERB1234000000000089200861",
      "allocationKWh": 407.00
    },
    {
      "meteringPoint": "VERB1234000000000089200862",
      "allocationKWh": 396.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200871",
      "allocationKWh": 279.00
    },
    {
      "meteringPoint": "VERB1234000000000089200872",
      "allocationKWh": 28.00
    },
    {
      "meteringPoint": "VERB1234000000000089200881",
      "allocationKWh": 332.00
    },
    {
      "meteringPoint": "VERB1234000000000089200891",
      "allocationKWh": 16.00
    },
    {
      "meteringPoint": "VERB1234000000000089200901",
      "allocationKWh": 301.00
    },
    {
      "meteringPoint": "VERB1234000000000089200902",
      "allocationKWh": 234.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200911",
      "allocationKWh": 434.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200921",
      "allocationKWh": 167.00
    },
    {
      "meteringPoint": "VERB1234000000000089200922",
      "allocationKWh": 112.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200931",
      "allocationKWh": 280.00
    },
    {
      "meteringPoint": "VERB1234000000000089200932",
      "allocationKWh": 416.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200933",
      "allocationKWh": 36.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200941",
      "allocationKWh": 201.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200942",
      "allocationKWh": 278.00
    },
    {
      "meteringPoint": "VERB1234000000000089200943",
      "allocationKWh": 67.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200951",
      "allocationKWh": 342.00
    },
    {
      "meteringPoint": "VERB1234000000000089200952",
      "allocationKWh": 334.00
    },
    {
      "meteringPoint": "VERB1234000000000089200953",
      "allocationKWh": 499.00
    },
    {
      "meteringPoint": "VERB1234000000000089200961",
      "allocationKWh": 224.00
    },
    {
      "meteringPoint": "VERB1234000000000089200962",
      "allocationKWh": 474.00
    },
    {
      "meteringPoint": "ERZ1234000000000089200971",
      "allocationKWh": 204.00
    },
    {
      "meteringPoint": "VERB1234000000000089200972",
      "allocationKWh": 40.00
    },
    {
      "meteringPoint": "VERB1234000000000089200981",
      "allocationKWh": 436.00
    },
    {
      "meteringPoint": "VERB1234000000000089200982",
      "allocationKWh": 200.00
    },
    {
      "meteringPoint": "VERB1234000000000089200991",
      "allocationKWh": 185.00
    },
    {
      "meteringPoint": "VERB1234000000000089200992",
      "allocationKWh": 63.00
    },
    {
      "meteringPoint": "VERB1234000000000089201001",
      "allocationKWh": 217.00
    },
    {
      "meteringPoint": "VERB1234000000000089201002",
      "allocationKWh": 294.00
    },
    {
      "meteringPoint": "VERB1234000000000089201011",
      "allocationKWh": 130.00
    },
    {
      "meteringPoint": "VERB1234000000000089201012",
      "allocationKWh": 280.00
    },
    {
      "meteringPoint": "VERB1234000000000089201021",
      "allocationKWh": 62.00
    },
    {
      "meteringPoint": "VERB1234000000000089201031",
      "allocationKWh": 465.00
    },
    {
      "meteringPoint": "VERB1234000000000089201032",
      "allocationKWh": 182.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201041",
      "allocationKWh": 230.00
    },
    {
      "meteringPoint": "VERB1234000000000089201051",
      "allocationKWh": 438.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201052",
      "allocationKWh": 195.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201131",
      "allocationKWh": 384.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201053",
      "allocationKWh": 442.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201061",
      "allocationKWh": 62.00
    },
    {
      "meteringPoint": "VERB1234000000000089201071",
      "allocationKWh": 271.00
    },
    {
      "meteringPoint": "VERB1234000000000089201072",
      "allocationKWh": 230.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201081",
      "allocationKWh": 127.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201082",
      "allocationKWh": 453.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201091",
      "allocationKWh": 347.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201092",
      "allocationKWh": 311.00
    },
    {
      "meteringPoint": "VERB1234000000000089201101",
      "allocationKWh": 245.00
    },
    {
      "meteringPoint": "VERB1234000000000089201102",
      "allocationKWh": 402.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201111",
      "allocationKWh": 168.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201112",
      "allocationKWh": 296.00
    },
    {
      "meteringPoint": "VERB1234000000000089201113",
      "allocationKWh": 306.00
    },
    {
      "meteringPoint": "VERB1234000000000089201121",
      "allocationKWh": 148.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201122",
      "allocationKWh": 60.00
    },
    {
      "meteringPoint": "VERB1234000000000089201132",
      "allocationKWh": 407.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201133",
      "allocationKWh": 263.00
    },
    {
      "meteringPoint": "VERB1234000000000089201141",
      "allocationKWh": 400.00
    },
    {
      "meteringPoint": "VERB1234000000000089201142",
      "allocationKWh": 75.00
    },
    {
      "meteringPoint": "VERB1234000000000089201143",
      "allocationKWh": 101.00
    },
    {
      "meteringPoint": "VERB1234000000000089201151",
      "allocationKWh": 473.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201152",
      "allocationKWh": 351.00
    },
    {
      "meteringPoint": "VERB1234000000000089201153",
      "allocationKWh": 254.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201161",
      "allocationKWh": 457.00
    },
    {
      "meteringPoint": "VERB1234000000000089201171",
      "allocationKWh": 17.00
    },
    {
      "meteringPoint": "VERB1234000000000089201172",
      "allocationKWh": 457.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201173",
      "allocationKWh": 438.00
    },
    {
      "meteringPoint": "VERB1234000000000089201181",
      "allocationKWh": 452.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201182",
      "allocationKWh": 475.00
    },
    {
      "meteringPoint": "VERB1234000000000089201191",
      "allocationKWh": 390.00
    },
    {
      "meteringPoint": "VERB1234000000000089201192",
      "allocationKWh": 220.00
    },
    {
      "meteringPoint": "VERB1234000000000089201201",
      "allocationKWh": 43.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201202",
      "allocationKWh": 124.00
    },
    {
      "meteringPoint": "VERB1234000000000089201203",
      "allocationKWh": 383.00
    },
    {
      "meteringPoint": "VERB1234000000000089201211",
      "allocationKWh": 478.00
    },
    {
      "meteringPoint": "VERB1234000000000089201212",
      "allocationKWh": 293.00
    },
    {
      "meteringPoint": "VERB1234000000000089201221",
      "allocationKWh": 315.00
    },
    {
      "meteringPoint": "VERB1234000000000089201222",
      "allocationKWh": 19.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201223",
      "allocationKWh": 354.00
    },
    {
      "meteringPoint": "VERB1234000000000089201231",
      "allocationKWh": 148.00
    },
    {
      "meteringPoint": "VERB1234000000000089201232",
      "allocationKWh": 126.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201241",
      "allocationKWh": 171.00
    },
    {
      "meteringPoint": "VERB1234000000000089201242",
      "allocationKWh": 492.00
    },
    {
      "meteringPoint": "VERB1234000000000089201251",
      "allocationKWh": 154.00
    },
    {
      "meteringPoint": "VERB1234000000000089201261",
      "allocationKWh": 372.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201271",
      "allocationKWh": 324.00
    },
    {
      "meteringPoint": "VERB1234000000000089201272",
      "allocationKWh": 95.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201273",
      "allocationKWh": 300.00
    },
    {
      "meteringPoint": "VERB1234000000000089201281",
      "allocationKWh": 334.00
    },
    {
      "meteringPoint": "VERB1234000000000089201282",
      "allocationKWh": 48.00
    },
    {
      "meteringPoint": "VERB1234000000000089201283",
      "allocationKWh": 199.00
    },
    {
      "meteringPoint": "VERB1234000000000089201291",
      "allocationKWh": 459.00
    },
    {
      "meteringPoint": "VERB1234000000000089201292",
      "allocationKWh": 416.00
    },
    {
      "meteringPoint": "VERB1234000000000089201301",
      "allocationKWh": 297.00
    },
    {
      "meteringPoint": "VERB1234000000000089201302",
      "allocationKWh": 350.00
    },
    {
      "meteringPoint": "VERB1234000000000089201303",
      "allocationKWh": 444.00
    },
    {
      "meteringPoint": "VERB1234000000000089201311",
      "allocationKWh": 379.00
    },
    {
      "meteringPoint": "VERB1234000000000089201312",
      "allocationKWh": 74.00
    },
    {
      "meteringPoint": "VERB1234000000000089201321",
      "allocationKWh": 121.00
    },
    {
      "meteringPoint": "VERB1234000000000089201322",
      "allocationKWh": 204.00
    },
    {
      "meteringPoint": "VERB1234000000000089201331",
      "allocationKWh": 410.00
    },
    {
      "meteringPoint": "VERB1234000000000089201332",
      "allocationKWh": 406.00
    },
    {
      "meteringPoint": "VERB1234000000000089201341",
      "allocationKWh": 61.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201342",
      "allocationKWh": 309.00
    },
    {
      "meteringPoint": "VERB1234000000000089201351",
      "allocationKWh": 199.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201361",
      "allocationKWh": 195.00
    },
    {
      "meteringPoint": "VERB1234000000000089201362",
      "allocationKWh": 254.00
    },
    {
      "meteringPoint": "VERB1234000000000089201371",
      "allocationKWh": 181.00
    },
    {
      "meteringPoint": "VERB1234000000000089201372",
      "allocationKWh": 254.00
    },
    {
      "meteringPoint": "VERB1234000000000089201381",
      "allocationKWh": 462.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201382",
      "allocationKWh": 128.00
    },
    {
      "meteringPoint": "VERB1234000000000089201391",
      "allocationKWh": 235.00
    },
    {
      "meteringPoint": "VERB1234000000000089201401",
      "allocationKWh": 304.00
    },
    {
      "meteringPoint": "VERB1234000000000089201402",
      "allocationKWh": 80.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201403",
      "allocationKWh": 376.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201411",
      "allocationKWh": 41.00
    },
    {
      "meteringPoint": "VERB1234000000000089201412",
      "allocationKWh": 61.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201421",
      "allocationKWh": 85.00
    },
    {
      "meteringPoint": "VERB1234000000000089201431",
      "allocationKWh": 475.00
    },
    {
      "meteringPoint": "VERB1234000000000089201432",
      "allocationKWh": 285.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201441",
      "allocationKWh": 188.00
    },
    {
      "meteringPoint": "VERB1234000000000089201451",
      "allocationKWh": 121.00
    },
    {
      "meteringPoint": "VERB1234000000000089201452",
      "allocationKWh": 101.00
    },
    {
      "meteringPoint": "VERB1234000000000089201461",
      "allocationKWh": 175.00
    },
    {
      "meteringPoint": "VERB1234000000000089201462",
      "allocationKWh": 50.00
    },
    {
      "meteringPoint": "VERB1234000000000089201463",
      "allocationKWh": 338.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201471",
      "allocationKWh": 324.00
    },
    {
      "meteringPoint": "VERB1234000000000089201472",
      "allocationKWh": 456.00
    },
    {
      "meteringPoint": "VERB1234000000000089201481",
      "allocationKWh": 33.00
    },
    {
      "meteringPoint": "VERB1234000000000089201491",
      "allocationKWh": 304.00
    },
    {
      "meteringPoint": "VERB1234000000000089201492",
      "allocationKWh": 226.00
    },
    {
      "meteringPoint": "VERB1234000000000089201501",
      "allocationKWh": 496.00
    },
    {
      "meteringPoint": "VERB1234000000000089201502",
      "allocationKWh": 457.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201503",
      "allocationKWh": 383.00
    },
    {
      "meteringPoint": "VERB1234000000000089201511",
      "allocationKWh": 433.00
    },
    {
      "meteringPoint": "VERB1234000000000089201512",
      "allocationKWh": 118.00
    },
    {
      "meteringPoint": "VERB1234000000000089201521",
      "allocationKWh": 340.00
    },
    {
      "meteringPoint": "VERB1234000000000089201531",
      "allocationKWh": 493.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201532",
      "allocationKWh": 121.00
    },
    {
      "meteringPoint": "VERB1234000000000089201541",
      "allocationKWh": 408.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201542",
      "allocationKWh": 407.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201551",
      "allocationKWh": 354.00
    },
    {
      "meteringPoint": "VERB1234000000000089201552",
      "allocationKWh": 84.00
    },
    {
      "meteringPoint": "VERB1234000000000089201561",
      "allocationKWh": 477.00
    },
    {
      "meteringPoint": "VERB1234000000000089201651",
      "allocationKWh": 325.00
    },
    {
      "meteringPoint": "VERB1234000000000089201562",
      "allocationKWh": 150.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201571",
      "allocationKWh": 390.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201572",
      "allocationKWh": 58.00
    },
    {
      "meteringPoint": "VERB1234000000000089201581",
      "allocationKWh": 457.00
    },
    {
      "meteringPoint": "VERB1234000000000089201591",
      "allocationKWh": 460.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201592",
      "allocationKWh": 155.00
    },
    {
      "meteringPoint": "VERB1234000000000089201593",
      "allocationKWh": 56.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201601",
      "allocationKWh": 169.00
    },
    {
      "meteringPoint": "VERB1234000000000089201602",
      "allocationKWh": 115.00
    },
    {
      "meteringPoint": "VERB1234000000000089201611",
      "allocationKWh": 445.00
    },
    {
      "meteringPoint": "VERB1234000000000089201621",
      "allocationKWh": 390.00
    },
    {
      "meteringPoint": "VERB1234000000000089201631",
      "allocationKWh": 193.00
    },
    {
      "meteringPoint": "VERB1234000000000089201632",
      "allocationKWh": 310.00
    },
    {
      "meteringPoint": "VERB1234000000000089201641",
      "allocationKWh": 470.00
    },
    {
      "meteringPoint": "VERB1234000000000089201642",
      "allocationKWh": 233.00
    },
    {
      "meteringPoint": "VERB1234000000000089201661",
      "allocationKWh": 468.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201671",
      "allocationKWh": 392.00
    },
    {
      "meteringPoint": "VERB1234000000000089201672",
      "allocationKWh": 305.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201673",
      "allocationKWh": 461.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201681",
      "allocationKWh": 137.00
    },
    {
      "meteringPoint": "VERB1234000000000089201691",
      "allocationKWh": 421.00
    },
    {
      "meteringPoint": "VERB1234000000000089201701",
      "allocationKWh": 72.00
    },
    {
      "meteringPoint": "VERB1234000000000089201702",
      "allocationKWh": 484.00
    },
    {
      "meteringPoint": "VERB1234000000000089201711",
      "allocationKWh": 175.00
    },
    {
      "meteringPoint": "VERB1234000000000089201721",
      "allocationKWh": 376.00
    },
    {
      "meteringPoint": "VERB1234000000000089201731",
      "allocationKWh": 166.00
    },
    {
      "meteringPoint": "VERB1234000000000089201732",
      "allocationKWh": 84.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201741",
      "allocationKWh": 116.00
    },
    {
      "meteringPoint": "VERB1234000000000089201751",
      "allocationKWh": 30.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201752",
      "allocationKWh": 498.00
    },
    {
      "meteringPoint": "VERB1234000000000089201761",
      "allocationKWh": 27.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201771",
      "allocationKWh": 168.00
    },
    {
      "meteringPoint": "VERB1234000000000089201772",
      "allocationKWh": 188.00
    },
    {
      "meteringPoint": "VERB1234000000000089201773",
      "allocationKWh": 407.00
    },
    {
      "meteringPoint": "VERB1234000000000089201781",
      "allocationKWh": 446.00
    },
    {
      "meteringPoint": "VERB1234000000000089201791",
      "allocationKWh": 334.00
    },
    {
      "meteringPoint": "VERB1234000000000089201792",
      "allocationKWh": 404.00
    },
    {
      "meteringPoint": "VERB1234000000000089201793",
      "allocationKWh": 193.00
    },
    {
      "meteringPoint": "VERB1234000000000089201801",
      "allocationKWh": 438.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201802",
      "allocationKWh": 100.00
    },
    {
      "meteringPoint": "VERB1234000000000089201811",
      "allocationKWh": 239.00
    },
    {
      "meteringPoint": "VERB1234000000000089201812",
      "allocationKWh": 311.00
    },
    {
      "meteringPoint": "VERB1234000000000089201821",
      "allocationKWh": 353.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201822",
      "allocationKWh": 41.00
    },
    {
      "meteringPoint": "VERB1234000000000089201831",
      "allocationKWh": 416.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201841",
      "allocationKWh": 379.00
    },
    {
      "meteringPoint": "VERB1234000000000089201842",
      "allocationKWh": 253.00
    },
    {
      "meteringPoint": "VERB1234000000000089201851",
      "allocationKWh": 265.00
    },
    {
      "meteringPoint": "VERB1234000000000089201852",
      "allocationKWh": 129.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201861",
      "allocationKWh": 110.00
    },
    {
      "meteringPoint": "VERB1234000000000089201862",
      "allocationKWh": 304.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201871",
      "allocationKWh": 191.00
    },
    {
      "meteringPoint": "VERB1234000000000089201881",
      "allocationKWh": 145.00
    },
    {
      "meteringPoint": "VERB1234000000000089201891",
      "allocationKWh": 312.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201892",
      "allocationKWh": 201.00
    },
    {
      "meteringPoint": "VERB1234000000000089201901",
      "allocationKWh": 164.00
    },
    {
      "meteringPoint": "VERB1234000000000089201902",
      "allocationKWh": 420.00
    },
    {
      "meteringPoint": "VERB1234000000000089201911",
      "allocationKWh": 493.00
    },
    {
      "meteringPoint": "VERB1234000000000089201912",
      "allocationKWh": 429.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201921",
      "allocationKWh": 306.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201931",
      "allocationKWh": 321.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201932",
      "allocationKWh": 120.00
    },
    {
      "meteringPoint": "VERB1234000000000089201933",
      "allocationKWh": 178.00
    },
    {
      "meteringPoint": "VERB1234000000000089201941",
      "allocationKWh": 174.00
    },
    {
      "meteringPoint": "VERB1234000000000089201942",
      "allocationKWh": 67.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201951",
      "allocationKWh": 449.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201952",
      "allocationKWh": 91.00
    },
    {
      "meteringPoint": "VERB1234000000000089201961",
      "allocationKWh": 144.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201962",
      "allocationKWh": 251.00
    },
    {
      "meteringPoint": "VERB1234000000000089201971",
      "allocationKWh": 423.00
    },
    {
      "meteringPoint": "VERB1234000000000089201972",
      "allocationKWh": 85.00
    },
    {
      "meteringPoint": "VERB1234000000000089201981",
      "allocationKWh": 290.00
    },
    {
      "meteringPoint": "ERZ1234000000000089201982",
      "allocationKWh": 244.00
    },
    {
      "meteringPoint": "VERB1234000000000089201991",
      "allocationKWh": 166.00
    },
    {
      "meteringPoint": "VERB1234000000000089202001",
      "allocationKWh": 256.00
    },
    {
      "meteringPoint": "VERB1234000000000089202002",
      "allocationKWh": 450.00
    },
    {
      "meteringPoint": "VERB1234000000000089202011",
      "allocationKWh": 289.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202012",
      "allocationKWh": 69.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202021",
      "allocationKWh": 472.00
    },
    {
      "meteringPoint": "VERB1234000000000089202031",
      "allocationKWh": 356.00
    },
    {
      "meteringPoint": "VERB1234000000000089202041",
      "allocationKWh": 143.00
    },
    {
      "meteringPoint": "VERB1234000000000089202042",
      "allocationKWh": 34.00
    },
    {
      "meteringPoint": "VERB1234000000000089202051",
      "allocationKWh": 420.00
    },
    {
      "meteringPoint": "VERB1234000000000089202052",
      "allocationKWh": 68.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202061",
      "allocationKWh": 43.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202062",
      "allocationKWh": 81.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202071",
      "allocationKWh": 475.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202072",
      "allocationKWh": 205.00
    },
    {
      "meteringPoint": "VERB1234000000000089202081",
      "allocationKWh": 480.00
    },
    {
      "meteringPoint": "VERB1234000000000089202082",
      "allocationKWh": 462.00
    },
    {
      "meteringPoint": "VERB1234000000000089202091",
      "allocationKWh": 257.00
    },
    {
      "meteringPoint": "VERB1234000000000089202092",
      "allocationKWh": 359.00
    },
    {
      "meteringPoint": "VERB1234000000000089202093",
      "allocationKWh": 257.00
    },
    {
      "meteringPoint": "VERB1234000000000089202101",
      "allocationKWh": 395.00
    },
    {
      "meteringPoint": "VERB1234000000000089202111",
      "allocationKWh": 500.00
    },
    {
      "meteringPoint": "VERB1234000000000089202112",
      "allocationKWh": 73.00
    },
    {
      "meteringPoint": "VERB1234000000000089202121",
      "allocationKWh": 136.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202122",
      "allocationKWh": 279.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202123",
      "allocationKWh": 302.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202131",
      "allocationKWh": 479.00
    },
    {
      "meteringPoint": "VERB1234000000000089202141",
      "allocationKWh": 410.00
    },
    {
      "meteringPoint": "VERB1234000000000089202151",
      "allocationKWh": 368.00
    },
    {
      "meteringPoint": "VERB1234000000000089202161",
      "allocationKWh": 175.00
    },
    {
      "meteringPoint": "VERB1234000000000089202171",
      "allocationKWh": 465.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202172",
      "allocationKWh": 385.00
    },
    {
      "meteringPoint": "VERB1234000000000089202181",
      "allocationKWh": 16.00
    },
    {
      "meteringPoint": "VERB1234000000000089202182",
      "allocationKWh": 297.00
    },
    {
      "meteringPoint": "VERB1234000000000089202191",
      "allocationKWh": 368.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202192",
      "allocationKWh": 12.00
    },
    {
      "meteringPoint": "VERB1234000000000089202201",
      "allocationKWh": 106.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202202",
      "allocationKWh": 198.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202211",
      "allocationKWh": 486.00
    },
    {
      "meteringPoint": "VERB1234000000000089202212",
      "allocationKWh": 489.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202221",
      "allocationKWh": 210.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202222",
      "allocationKWh": 337.00
    },
    {
      "meteringPoint": "VERB1234000000000089202231",
      "allocationKWh": 415.00
    },
    {
      "meteringPoint": "VERB1234000000000089202232",
      "allocationKWh": 343.00
    },
    {
      "meteringPoint": "VERB1234000000000089202241",
      "allocationKWh": 238.00
    },
    {
      "meteringPoint": "VERB1234000000000089202242",
      "allocationKWh": 17.00
    },
    {
      "meteringPoint": "VERB1234000000000089202251",
      "allocationKWh": 254.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202261",
      "allocationKWh": 218.00
    },
    {
      "meteringPoint": "VERB1234000000000089202271",
      "allocationKWh": 91.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202281",
      "allocationKWh": 176.00
    },
    {
      "meteringPoint": "VERB1234000000000089202282",
      "allocationKWh": 195.00
    },
    {
      "meteringPoint": "VERB1234000000000089202283",
      "allocationKWh": 204.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202291",
      "allocationKWh": 48.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202301",
      "allocationKWh": 11.00
    },
    {
      "meteringPoint": "VERB1234000000000089202302",
      "allocationKWh": 221.00
    },
    {
      "meteringPoint": "VERB1234000000000089202303",
      "allocationKWh": 202.00
    },
    {
      "meteringPoint": "VERB1234000000000089202311",
      "allocationKWh": 283.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202312",
      "allocationKWh": 66.00
    },
    {
      "meteringPoint": "VERB1234000000000089202321",
      "allocationKWh": 174.00
    },
    {
      "meteringPoint": "VERB1234000000000089202322",
      "allocationKWh": 19.00
    },
    {
      "meteringPoint": "VERB1234000000000089202323",
      "allocationKWh": 367.00
    },
    {
      "meteringPoint": "VERB1234000000000089202331",
      "allocationKWh": 193.00
    },
    {
      "meteringPoint": "VERB1234000000000089202341",
      "allocationKWh": 464.00
    },
    {
      "meteringPoint": "VERB1234000000000089202351",
      "allocationKWh": 171.00
    },
    {
      "meteringPoint": "VERB1234000000000089202361",
      "allocationKWh": 69.00
    },
    {
      "meteringPoint": "VERB1234000000000089202362",
      "allocationKWh": 345.00
    },
    {
      "meteringPoint": "VERB1234000000000089202371",
      "allocationKWh": 123.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202381",
      "allocationKWh": 391.00
    },
    {
      "meteringPoint": "VERB1234000000000089202382",
      "allocationKWh": 114.00
    },
    {
      "meteringPoint": "VERB1234000000000089202383",
      "allocationKWh": 269.00
    },
    {
      "meteringPoint": "VERB1234000000000089202391",
      "allocationKWh": 28.00
    },
    {
      "meteringPoint": "VERB1234000000000089202401",
      "allocationKWh": 360.00
    },
    {
      "meteringPoint": "VERB1234000000000089202402",
      "allocationKWh": 36.00
    },
    {
      "meteringPoint": "VERB1234000000000089202411",
      "allocationKWh": 428.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202421",
      "allocationKWh": 455.00
    },
    {
      "meteringPoint": "VERB1234000000000089202431",
      "allocationKWh": 424.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202432",
      "allocationKWh": 367.00
    },
    {
      "meteringPoint": "VERB1234000000000089202441",
      "allocationKWh": 304.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202442",
      "allocationKWh": 183.00
    },
    {
      "meteringPoint": "VERB1234000000000089202443",
      "allocationKWh": 115.00
    },
    {
      "meteringPoint": "VERB1234000000000089202451",
      "allocationKWh": 484.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202452",
      "allocationKWh": 208.00
    },
    {
      "meteringPoint": "VERB1234000000000089202453",
      "allocationKWh": 429.00
    },
    {
      "meteringPoint": "VERB1234000000000089202461",
      "allocationKWh": 370.00
    },
    {
      "meteringPoint": "VERB1234000000000089202462",
      "allocationKWh": 347.00
    },
    {
      "meteringPoint": "VERB1234000000000089202471",
      "allocationKWh": 177.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202472",
      "allocationKWh": 243.00
    },
    {
      "meteringPoint": "VERB1234000000000089202481",
      "allocationKWh": 189.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202482",
      "allocationKWh": 151.00
    },
    {
      "meteringPoint": "VERB1234000000000089202491",
      "allocationKWh": 28.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202501",
      "allocationKWh": 456.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202502",
      "allocationKWh": 264.00
    },
    {
      "meteringPoint": "VERB1234000000000089202503",
      "allocationKWh": 115.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202511",
      "allocationKWh": 27.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202512",
      "allocationKWh": 33.00
    },
    {
      "meteringPoint": "VERB1234000000000089202521",
      "allocationKWh": 256.00
    },
    {
      "meteringPoint": "VERB1234000000000089202531",
      "allocationKWh": 271.00
    },
    {
      "meteringPoint": "VERB1234000000000089202532",
      "allocationKWh": 161.00
    },
    {
      "meteringPoint": "VERB1234000000000089202541",
      "allocationKWh": 312.00
    },
    {
      "meteringPoint": "VERB1234000000000089202542",
      "allocationKWh": 212.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202551",
      "allocationKWh": 160.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202552",
      "allocationKWh": 351.00
    },
    {
      "meteringPoint": "VERB1234000000000089202561",
      "allocationKWh": 142.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202562",
      "allocationKWh": 183.00
    },
    {
      "meteringPoint": "VERB1234000000000089202563",
      "allocationKWh": 276.00
    },
    {
      "meteringPoint": "VERB1234000000000089202571",
      "allocationKWh": 400.00
    },
    {
      "meteringPoint": "VERB1234000000000089202572",
      "allocationKWh": 246.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202581",
      "allocationKWh": 400.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202582",
      "allocationKWh": 13.00
    },
    {
      "meteringPoint": "VERB1234000000000089202591",
      "allocationKWh": 354.00
    },
    {
      "meteringPoint": "VERB1234000000000089202601",
      "allocationKWh": 359.00
    },
    {
      "meteringPoint": "VERB1234000000000089202602",
      "allocationKWh": 267.00
    },
    {
      "meteringPoint": "VERB1234000000000089202611",
      "allocationKWh": 251.00
    },
    {
      "meteringPoint": "VERB1234000000000089202612",
      "allocationKWh": 496.00
    },
    {
      "meteringPoint": "VERB1234000000000089202621",
      "allocationKWh": 122.00
    },
    {
      "meteringPoint": "VERB1234000000000089202622",
      "allocationKWh": 12.00
    },
    {
      "meteringPoint": "VERB1234000000000089202623",
      "allocationKWh": 253.00
    },
    {
      "meteringPoint": "VERB1234000000000089202631",
      "allocationKWh": 102.00
    },
    {
      "meteringPoint": "VERB1234000000000089202632",
      "allocationKWh": 245.00
    },
    {
      "meteringPoint": "VERB1234000000000089202641",
      "allocationKWh": 94.00
    },
    {
      "meteringPoint": "VERB1234000000000089202721",
      "allocationKWh": 238.00
    },
    {
      "meteringPoint": "VERB1234000000000089202642",
      "allocationKWh": 189.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202651",
      "allocationKWh": 10.00
    },
    {
      "meteringPoint": "VERB1234000000000089202652",
      "allocationKWh": 388.00
    },
    {
      "meteringPoint": "VERB1234000000000089202661",
      "allocationKWh": 23.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202662",
      "allocationKWh": 344.00
    },
    {
      "meteringPoint": "VERB1234000000000089202671",
      "allocationKWh": 238.00
    },
    {
      "meteringPoint": "VERB1234000000000089202672",
      "allocationKWh": 359.00
    },
    {
      "meteringPoint": "VERB1234000000000089202681",
      "allocationKWh": 245.00
    },
    {
      "meteringPoint": "VERB1234000000000089202682",
      "allocationKWh": 414.00
    },
    {
      "meteringPoint": "VERB1234000000000089202691",
      "allocationKWh": 86.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202692",
      "allocationKWh": 476.00
    },
    {
      "meteringPoint": "VERB1234000000000089202701",
      "allocationKWh": 425.00
    },
    {
      "meteringPoint": "VERB1234000000000089202711",
      "allocationKWh": 331.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202712",
      "allocationKWh": 361.00
    },
    {
      "meteringPoint": "VERB1234000000000089202713",
      "allocationKWh": 481.00
    },
    {
      "meteringPoint": "VERB1234000000000089202722",
      "allocationKWh": 459.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202731",
      "allocationKWh": 256.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202732",
      "allocationKWh": 175.00
    },
    {
      "meteringPoint": "VERB1234000000000089202741",
      "allocationKWh": 277.00
    },
    {
      "meteringPoint": "VERB1234000000000089202742",
      "allocationKWh": 140.00
    },
    {
      "meteringPoint": "VERB1234000000000089202743",
      "allocationKWh": 101.00
    },
    {
      "meteringPoint": "VERB1234000000000089202751",
      "allocationKWh": 273.00
    },
    {
      "meteringPoint": "VERB1234000000000089202752",
      "allocationKWh": 43.00
    },
    {
      "meteringPoint": "VERB1234000000000089202753",
      "allocationKWh": 388.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202761",
      "allocationKWh": 462.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202771",
      "allocationKWh": 362.00
    },
    {
      "meteringPoint": "VERB1234000000000089202772",
      "allocationKWh": 206.00
    },
    {
      "meteringPoint": "VERB1234000000000089202781",
      "allocationKWh": 129.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202791",
      "allocationKWh": 256.00
    },
    {
      "meteringPoint": "VERB1234000000000089202801",
      "allocationKWh": 374.00
    },
    {
      "meteringPoint": "VERB1234000000000089202811",
      "allocationKWh": 166.00
    },
    {
      "meteringPoint": "VERB1234000000000089202812",
      "allocationKWh": 200.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202821",
      "allocationKWh": 159.00
    },
    {
      "meteringPoint": "VERB1234000000000089202822",
      "allocationKWh": 321.00
    },
    {
      "meteringPoint": "VERB1234000000000089202831",
      "allocationKWh": 243.00
    },
    {
      "meteringPoint": "VERB1234000000000089202832",
      "allocationKWh": 230.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202841",
      "allocationKWh": 102.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202842",
      "allocationKWh": 376.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202851",
      "allocationKWh": 376.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202852",
      "allocationKWh": 459.00
    },
    {
      "meteringPoint": "VERB1234000000000089202861",
      "allocationKWh": 324.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202862",
      "allocationKWh": 448.00
    },
    {
      "meteringPoint": "VERB1234000000000089202863",
      "allocationKWh": 411.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202871",
      "allocationKWh": 137.00
    },
    {
      "meteringPoint": "VERB1234000000000089202872",
      "allocationKWh": 196.00
    },
    {
      "meteringPoint": "VERB1234000000000089202881",
      "allocationKWh": 120.00
    },
    {
      "meteringPoint": "VERB1234000000000089202882",
      "allocationKWh": 351.00
    },
    {
      "meteringPoint": "VERB1234000000000089202891",
      "allocationKWh": 414.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202901",
      "allocationKWh": 106.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202902",
      "allocationKWh": 418.00
    },
    {
      "meteringPoint": "VERB1234000000000089202911",
      "allocationKWh": 461.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202912",
      "allocationKWh": 40.00
    },
    {
      "meteringPoint": "VERB1234000000000089202921",
      "allocationKWh": 42.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202922",
      "allocationKWh": 144.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202923",
      "allocationKWh": 160.00
    },
    {
      "meteringPoint": "VERB1234000000000089202931",
      "allocationKWh": 481.00
    },
    {
      "meteringPoint": "VERB1234000000000089202941",
      "allocationKWh": 290.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202942",
      "allocationKWh": 123.00
    },
    {
      "meteringPoint": "VERB1234000000000089202943",
      "allocationKWh": 394.00
    },
    {
      "meteringPoint": "ERZ1234000000000089202951",
      "allocationKWh": 184.00
    }
  ],
  "preview": true
}'