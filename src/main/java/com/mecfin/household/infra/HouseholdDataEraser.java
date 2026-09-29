package com.mecfin.household.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Apaga todos os dados de um household (exclusão de conta, Fase 14). SQL explícito e em ordem
 * de dependência, em vez de cascata espalhada pelas FKs: a lista abaixo é o inventário
 * auditável de onde mora dado do usuário - tabela nova com household_id precisa entrar aqui
 * (HouseholdSharingIT varre o information_schema e falha se sobrar linha).
 *
 * Tabelas com ON DELETE CASCADE (transaction_tags, recurring_series_tags, goal_contributions)
 * saem junto com a tabela-mãe.
 */
@Component
public class HouseholdDataEraser {

    private static final String ACCOUNTS = "(SELECT id FROM accounts WHERE household_id = :h)";
    private static final String CARDS = "(SELECT id FROM credit_cards WHERE household_id = :h)";

    // Ordem importa: quem referencia vem antes de quem é referenciado. Uma transferência tem
    // referência mútua entre as duas pernas (transfer_pair_id); o DELETE único de transactions
    // remove as duas no mesmo comando, e a FK (NO ACTION) só é checada no fim do comando.
    private static final List<String> STATEMENTS = List.of(
            "DELETE FROM notifications WHERE household_id = :h",
            "DELETE FROM bank_account_links WHERE household_id = :h",
            "DELETE FROM bank_connections WHERE household_id = :h",
            "DELETE FROM budgets WHERE household_id = :h",
            "DELETE FROM bills WHERE household_id = :h",
            "DELETE FROM credit_card_charges WHERE credit_card_invoice_id IN "
                    + "(SELECT id FROM credit_card_invoices WHERE credit_card_id IN " + CARDS + ")",
            "DELETE FROM credit_card_invoices WHERE credit_card_id IN " + CARDS,
            "DELETE FROM credit_cards WHERE household_id = :h",
            "DELETE FROM goals WHERE household_id = :h",
            "DELETE FROM categorization_rules WHERE household_id = :h",
            "DELETE FROM transactions WHERE account_id IN " + ACCOUNTS,
            "DELETE FROM import_batches WHERE household_id = :h",
            "DELETE FROM recurring_series WHERE household_id = :h",
            "DELETE FROM tags WHERE household_id = :h",
            "DELETE FROM accounts WHERE household_id = :h",
            "DELETE FROM categories WHERE household_id = :h",
            "DELETE FROM household_invites WHERE household_id = :h",
            "DELETE FROM household_members WHERE household_id = :h",
            "DELETE FROM households WHERE id = :h");

    private final NamedParameterJdbcTemplate jdbc;

    public HouseholdDataEraser(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Se o household tem algo que o usuário criou (conta, cartão, meta, conta a pagar, conexão
     * bancária). Categorias não contam: household novo não cria nenhuma (as padrão são globais).
     */
    public boolean hasFinancialData(UUID householdId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM accounts WHERE household_id = :h)
                    OR EXISTS (SELECT 1 FROM credit_cards WHERE household_id = :h)
                    OR EXISTS (SELECT 1 FROM goals WHERE household_id = :h)
                    OR EXISTS (SELECT 1 FROM bills WHERE household_id = :h)
                    OR EXISTS (SELECT 1 FROM bank_connections WHERE household_id = :h)
                """, new MapSqlParameterSource("h", householdId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public void erase(UUID householdId) {
        MapSqlParameterSource params = new MapSqlParameterSource("h", householdId);
        for (String statement : STATEMENTS) {
            jdbc.update(statement, params);
        }
    }
}
