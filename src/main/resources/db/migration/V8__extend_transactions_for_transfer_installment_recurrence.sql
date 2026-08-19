-- credit_card_invoice_id e external_id/bank_connection_id (Fases 8/9) ficam de fora de
-- proposito: colunas so entram quando os modulos que as usam existirem.
ALTER TABLE transactions
    ADD COLUMN transfer_pair_id UUID REFERENCES transactions(id),
    ADD COLUMN transfer_direction VARCHAR(10),
    ADD COLUMN installment_number INTEGER,
    ADD COLUMN installment_total INTEGER,
    ADD COLUMN installment_group_id UUID,
    ADD COLUMN recurrence_rule VARCHAR(20);

CREATE INDEX idx_transactions_installment_group_id ON transactions(installment_group_id)
    WHERE installment_group_id IS NOT NULL;
