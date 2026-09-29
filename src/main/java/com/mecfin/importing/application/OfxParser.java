package com.mecfin.importing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor de OFX tolerante às duas gerações do formato:
 * <ul>
 *   <li>OFX 1.x (SGML) — o que a maioria dos bancos brasileiros exporta: tags de campo SEM
 *       fechamento ({@code <TRNAMT>-50.00} até o fim da linha);</li>
 *   <li>OFX 2.x (XML) — com fechamento ({@code <TRNAMT>-50.00</TRNAMT>}).</li>
 * </ul>
 * Por isso não usa parser XML: extrai cada bloco STMTTRN (que tem fechamento nas duas versões)
 * e lê os campos até o próximo "<" ou quebra de linha.
 */
public final class OfxParser {

    private static final Pattern TRANSACTION = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter OFX_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OfxParser() {
    }

    public static boolean looksLikeOfx(String text) {
        String head = text.substring(0, Math.min(text.length(), 2000)).toUpperCase(Locale.ROOT);
        return head.contains("OFXHEADER") || head.contains("<OFX>");
    }

    public static List<StatementLine> parse(String text) {
        List<StatementLine> lines = new ArrayList<>();
        Matcher matcher = TRANSACTION.matcher(text);
        int position = 0;
        while (matcher.find()) {
            position++;
            String block = matcher.group(1);
            String rawAmount = field(block, "TRNAMT");
            String rawDate = field(block, "DTPOSTED");
            if (rawAmount == null || rawDate == null) {
                throw new StatementFormatException("Transação " + position + " do OFX sem valor ou data");
            }
            String memo = field(block, "MEMO");
            String name = field(block, "NAME");
            String description = memo != null && !memo.isBlank() ? memo : name;
            if (description == null || description.isBlank()) {
                description = "Lançamento importado";
            }
            BigDecimal amount = Amounts.parse(rawAmount);
            LocalDate date = parseDate(rawDate, position);
            String fitId = field(block, "FITID");
            // Sem FITID (raro, bancos pequenos): cai no mesmo hash determinístico do CSV.
            String externalId = fitId != null && !fitId.isBlank()
                    ? "ofx:" + fitId.strip()
                    : StatementIds.hash(date, amount, description, position);
            lines.add(new StatementLine(truncate(externalId, 120), date, amount, clean(description)));
        }
        if (lines.isEmpty()) {
            throw new StatementFormatException("Nenhuma transação encontrada no OFX (bloco STMTTRN)");
        }
        return lines;
    }

    private static String field(String block, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">\\s*([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(block);
        return m.find() ? m.group(1).strip() : null;
    }

    // DTPOSTED: AAAAMMDD[HHMMSS[.XXX]][[-3:BRT]] — só a data interessa.
    private static LocalDate parseDate(String raw, int position) {
        try {
            return LocalDate.parse(raw.strip().substring(0, 8), OFX_DATE);
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            throw new StatementFormatException("Data inválida na transação " + position + " do OFX: " + raw);
        }
    }

    private static String clean(String description) {
        String collapsed = description.replace("&amp;", "&").replaceAll("\\s+", " ").strip();
        return truncate(collapsed, 255);
    }

    static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
