ALTER TABLE shipments ADD COLUMN provider varchar(20) NOT NULL DEFAULT 'DELHIVERY';
ALTER TABLE shipments ADD COLUMN provider_reference varchar(80);
ALTER TABLE shipments ADD CONSTRAINT ck_shipment_provider CHECK(provider IN ('DELHIVERY','EASYSHIP'));
ALTER TABLE shipments ADD CONSTRAINT ck_easyship_reference CHECK(provider <> 'EASYSHIP' OR provider_reference IS NOT NULL);
CREATE UNIQUE INDEX uq_shipment_provider_reference ON shipments(provider,provider_reference) WHERE provider_reference IS NOT NULL;
