package com.mecfin.household.infra;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Portabilidade LGPD (art. 18, V) — o par do {@link HouseholdDataEraser}: mesmo inventário de
 * tabelas, agora para exportar. O JSON sai do próprio Postgres (to_jsonb/jsonb_agg), e as colunas
 * sensíveis são removidas NA CONSULTA — nunca chegam à aplicação, então não há como vazarem por
 * um log ou um mapeamento esquecido.
 *
 * <p>Tabela nova com household_id precisa entrar aqui ou em {@link #NOT_EXPORTED} com o motivo:
 * o HouseholdSharingIT falha se alguma ficar de fora das duas listas.
 */
@Component
public class HouseholdDataExporter {

    private record Section(String table, String where, List<String> hiddenColumns) {
    }

    private static final String ACCOUNTS = "(SELECT id FROM accounts WHERE household_id = :h)";
    private static final String CARDS = "(SELECT id FROM credit_cards WHERE household_id = :h)";
    private static final String BY_HOUSEHOLD = "household_id = :h";

    private static final List<Section> SECTIONS = List.of(
            new Section("households", "id = :h", List.of()),
            new Section("household_members", BY_HOUSEHOLD, List.of()),
            new Section("household_invites", BY_HOUSEHOLD, List.of("token_hash")),
            new Section("accounts", BY_HOUSEHOLD, List.of()),
            new Section("categories", BY_HOUSEHOLD, List.of()),
            new Section("tags", BY_HOUSEHOLD, List.of()),
            new Section("transactions", "account_id IN " + ACCOUNTS, List.of()),
            new Section("transaction_tags",
                    "transaction_id IN (SELECT id FROM transactions WHERE account_id IN " + ACCOUNTS + ")", List.of()),
            new Section("transaction_attachments", BY_HOUSEHOLD, List.of()),
            new Section("recurring_series", BY_HOUSEHOLD, List.of()),
            new Section("recurring_series_tags",
                    "series_id IN (SELECT id FROM recurring_series WHERE household_id = :h)", List.of()),
            new Section("bills", BY_HOUSEHOLD, List.of()),
            new Section("budgets", BY_HOUSEHOLD, List.of()),
            new Section("credit_cards", BY_HOUSEHOLD, List.of()),
            new Section("credit_card_invoices", "credit_card_id IN " + CARDS, List.of()),
            new Section("credit_card_charges",
                    "credit_card_invoice_id IN (SELECT id FROM credit_card_invoices WHERE credit_card_id IN "
                            + CARDS + ")", List.of()),
            new Section("goals", BY_HOUSEHOLD, List.of()),
            new Section("goal_contributions", "goal_id IN (SELECT id FROM goals WHERE household_id = :h)",
                    List.of()),
            new Section("categorization_rules", BY_HOUSEHOLD, List.of()),
            new Section("import_batches", BY_HOUSEHOLD, List.of()),
            new Section("notifications", BY_HOUSEHOLD, List.of()),
            new Section("bank_connections", BY_HOUSEHOLD, List.of("access_token_encrypted")),
            new Section("bank_account_links", BY_HOUSEHOLD, List.of()));

    /** Tabelas com household_id que ficam de fora de propósito (hoje: nenhuma). */
    public static final Map<String, String> NOT_EXPORTED = Map.of();

    private final NamedParameterJdbcTemplate jdbc;

    public HouseholdDataExporter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static List<String> exportedTables() {
        return SECTIONS.stream().map(Section::table).toList();
    }

    /** Tabela → array JSON (texto) com as linhas do household. */
    public Map<String, String> export(UUID householdId) {
        MapSqlParameterSource params = new MapSqlParameterSource("h", householdId);
        Map<String, String> out = new LinkedHashMap<>();
        for (Section section : SECTIONS) {
            String row = section.hiddenColumns().isEmpty()
                    ? "to_jsonb(t)"
                    : "to_jsonb(t) - ARRAY[" + String.join(", ",
                            section.hiddenColumns().stream().map(c -> "'" + c + "'").toList()) + "]::text[]";
            // Nomes de tabela/coluna vêm só da lista fixa acima, nunca de entrada do usuário.
            String sql = "SELECT coalesce(jsonb_agg(" + row + "), '[]'::jsonb)::text FROM " + section.table()
                    + " t WHERE " + section.where();
            out.put(section.table(), jdbc.queryForObject(sql, params, String.class));
        }
        return out;
    }
}
