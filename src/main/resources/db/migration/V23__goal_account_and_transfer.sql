-- Meta ligada à conta onde o dinheiro dela fica guardado (ex.: a poupança da reserva). Opcional:
-- sem conta, a meta continua sendo só um cofrinho virtual, como antes.
ALTER TABLE goals ADD COLUMN account_id UUID REFERENCES accounts(id);

-- Aporte/resgate feito junto com a transferência entre contas aponta para uma das pernas, para
-- excluir o aporte cancelar a transferência junto (e o saldo das contas voltar a bater).
ALTER TABLE goal_contributions ADD COLUMN transfer_transaction_id UUID REFERENCES transactions(id);
