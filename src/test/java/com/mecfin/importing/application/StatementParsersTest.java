package com.mecfin.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mecfin.importing.domain.CategorizationRule;
import com.mecfin.importing.domain.DescriptionNormalizer;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatementParsersTest {

    // OFX 1.02 SGML como os bancos brasileiros exportam: campos sem tag de fechamento, CHARSET 1252.
    private static final String OFX_SGML = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            ENCODING:USASCII
            CHARSET:1252

            <OFX>
            <BANKMSGSRSV1><STMTTRNRS><STMTRS>
            <BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20260903120000[-3:BRT]
            <TRNAMT>-45.90
            <FITID>202609030001
            <MEMO>COMPRA CARTAO PADARIA SÃO JOÃO
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20260905
            <TRNAMT>8200.00
            <FITID>202609050002
            <NAME>SALARIO EMPRESA X
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS></STMTTRNRS></BANKMSGSRSV1>
            </OFX>
            """;

    @Test
    void parsesSgmlOfxInWindows1252WithAccents() {
        byte[] bytes = OFX_SGML.getBytes(Charset.forName("windows-1252"));

        List<StatementLine> lines = OfxParser.parse(StatementText.decode(bytes));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo(new StatementLine("ofx:202609030001", LocalDate.of(2026, 9, 3),
                new BigDecimal("-45.90"), "COMPRA CARTAO PADARIA SÃO JOÃO"));
        assertThat(lines.get(1).description()).isEqualTo("SALARIO EMPRESA X");
        assertThat(lines.get(1).amount()).isEqualByComparingTo("8200");
    }

    @Test
    void parsesXmlOfxWithClosingTags() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><?OFX OFXHEADER="200" VERSION="220"?>
                <OFX><BANKTRANLIST><STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260910</DTPOSTED>
                <TRNAMT>-19,90</TRNAMT><FITID>abc-1</FITID><NAME>Streaming &amp; Cia</NAME></STMTTRN></BANKTRANLIST></OFX>
                """;

        List<StatementLine> lines = OfxParser.parse(xml);

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.externalId()).isEqualTo("ofx:abc-1");
            assertThat(line.amount()).isEqualByComparingTo("-19.90");
            assertThat(line.description()).isEqualTo("Streaming & Cia");
        });
    }

    @Test
    void ofxWithoutTransactionsIsRejected() {
        assertThatThrownBy(() -> OfxParser.parse("OFXHEADER:100\n<OFX></OFX>"))
                .isInstanceOf(StatementFormatException.class);
    }

    @Test
    void parsesBankStyleCsvSkippingBalanceLines() {
        String csv = """
                Data;Lançamento;Valor (R$);Saldo
                01/09/2026;SALDO ANTERIOR;;1.000,00
                02/09/2026;"PIX ENVIADO; JOAO";-1.234,56;
                03/09/2026;Mercado Extra;-89,90;
                03/09/2026;Mercado Extra;-89,90;
                05/09/2026;SALARIO;8.200,00;
                """;

        CsvStatementParser.Parsed parsed = CsvStatementParser.parse(csv, null);

        assertThat(parsed.columns()).containsExactly("Data", "Lançamento", "Valor (R$)", "Saldo");
        assertThat(parsed.mapping()).isEqualTo(new CsvMapping(0, 1, 2, false));
        assertThat(parsed.lines()).extracting(StatementLine::description)
                .containsExactly("PIX ENVIADO; JOAO", "Mercado Extra", "Mercado Extra", "SALARIO");
        assertThat(parsed.lines().get(0).amount()).isEqualByComparingTo("-1234.56");
        // duas compras idênticas no mesmo dia: IDs diferentes, e estáveis entre reimportações
        assertThat(parsed.lines().get(1).externalId()).isNotEqualTo(parsed.lines().get(2).externalId());
        assertThat(CsvStatementParser.parse(csv, null).lines().get(2).externalId())
                .isEqualTo(parsed.lines().get(2).externalId());
    }

    @Test
    void cardStyleCsvWithPositiveExpensesUsesInvertSign() {
        String csv = """
                date,title,amount
                2026-09-01,Padaria Real,12.50
                2026-09-02,Estorno loja,-30.00
                """;

        CsvStatementParser.Parsed detected = CsvStatementParser.parse(csv, null);
        CsvStatementParser.Parsed inverted = CsvStatementParser.parse(csv,
                new CsvMapping(detected.mapping().dateColumn(), detected.mapping().descriptionColumn(),
                        detected.mapping().amountColumn(), true));

        assertThat(inverted.lines().get(0).amount()).isEqualByComparingTo("-12.50");
        assertThat(inverted.lines().get(1).amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void csvReportsTheLineOfABadValue() {
        String csv = "Data;Descrição;Valor\n01/09/2026;Café;abc\n";

        assertThatThrownBy(() -> CsvStatementParser.parse(csv, null))
                .isInstanceOf(StatementFormatException.class)
                .hasMessageContaining("Linha 2");
    }

    @Test
    void amountsInEveryRealWorldShape() {
        assertThat(Amounts.parse("-1.234,56")).isEqualByComparingTo("-1234.56");
        assertThat(Amounts.parse("1,234.56")).isEqualByComparingTo("1234.56");
        assertThat(Amounts.parse("R$ 89,90")).isEqualByComparingTo("89.90");
        assertThat(Amounts.parse("(45,00)")).isEqualByComparingTo("-45.00");
        assertThat(Amounts.parse("12.5")).isEqualByComparingTo("12.50");
        assertThat(Amounts.parse("1.500")).isEqualByComparingTo("1500");
        assertThat(Amounts.parse("100-")).isEqualByComparingTo("-100");
        assertThatThrownBy(() -> Amounts.parse("12,3,4")).isInstanceOf(StatementFormatException.class);
    }

    @Test
    void utf8IsKeptAndInvalidUtf8FallsBackToWindows1252() {
        assertThat(StatementText.decode("Pão".getBytes(StandardCharsets.UTF_8))).isEqualTo("Pão");
        assertThat(StatementText.decode("Pão".getBytes(Charset.forName("windows-1252")))).isEqualTo("Pão");
    }

    @Test
    void normalizerIgnoresCaseAccentsPunctuationAndNumbers() {
        assertThat(DescriptionNormalizer.normalize("COMPRA CARTÃO 4432 Padaria São João 12/09"))
                .isEqualTo("compra cartao padaria sao joao");
        assertThat(DescriptionNormalizer.normalize("Compra cartao 9981 PADARIA SAO JOAO 03/10"))
                .isEqualTo("compra cartao padaria sao joao");
    }

    @Test
    void historyLearnsFromWholeWordDescriptionsPreferringTheLongest() {
        UUID groceries = UUID.randomUUID();
        UUID bakery = UUID.randomUUID();
        UUID transfers = UUID.randomUUID();
        java.util.Map<String, UUID> history = java.util.Map.of(
                "mercado", groceries,
                "padaria sao joao", bakery,
                "pix", transfers);

        assertThat(ImportService.learnedCategory(history, DescriptionNormalizer.normalize("COMPRA CARTAO 4432 MERCADO EXTRA")))
                .isEqualTo(groceries);
        assertThat(ImportService.learnedCategory(history, "compra padaria sao joao centro")).isEqualTo(bakery);
        // palavra inteira: "supermercado" não é "mercado"; e "pix" é curto demais para casar sozinho
        assertThat(ImportService.learnedCategory(history, "supermercado bom preco")).isNull();
        assertThat(ImportService.learnedCategory(history, "pix enviado joao")).isNull();
        assertThat(ImportService.learnedCategory(history, "pix")).isEqualTo(transfers);
    }

    @Test
    void mostSpecificRuleWins() {
        UUID general = UUID.randomUUID();
        UUID specific = UUID.randomUUID();
        List<CategorizationRule> rules = List.of(
                new CategorizationRule(UUID.randomUUID(), "uber", general, null),
                new CategorizationRule(UUID.randomUUID(), "Uber Eats", specific, null));

        assertThat(CategorizationRuleService.bestMatch(rules, DescriptionNormalizer.normalize("UBER *EATS PENDING")))
                .get().extracting(CategorizationRule::getCategoryId).isEqualTo(specific);
        assertThat(CategorizationRuleService.bestMatch(rules, DescriptionNormalizer.normalize("UBER TRIP")))
                .get().extracting(CategorizationRule::getCategoryId).isEqualTo(general);
        assertThat(CategorizationRuleService.bestMatch(rules, "ifood")).isEmpty();
    }
}
