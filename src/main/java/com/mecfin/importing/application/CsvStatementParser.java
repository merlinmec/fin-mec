package com.mecfin.importing.application;

import com.mecfin.importing.domain.DescriptionNormalizer;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leitor de CSV de extrato. Cada banco exporta de um jeito, então tudo é detectado e pode ser
 * corrigido pelo usuário na pré-visualização: separador (";", "," ou tab), se a primeira linha é
 * cabeçalho, quais colunas são data/descrição/valor (por palavra-chave do cabeçalho), formato
 * de data (dd/MM/yyyy, yyyy-MM-dd, dd-MM-yyyy, dd/MM/yy) e de valor (ver Amounts).
 */
public final class CsvStatementParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uu"),
            DateTimeFormatter.ofPattern("dd.MM.uuuu"));

    /** Resultado bruto: cabeçalho (ou "Coluna N"), linhas, e o mapeamento efetivamente usado. */
    public record Parsed(List<String> columns, CsvMapping mapping, List<StatementLine> lines) {
    }

    private CsvStatementParser() {
    }

    public static Parsed parse(String text, CsvMapping requested) {
        List<List<String>> rows = new ArrayList<>();
        char delimiter = detectDelimiter(text);
        for (String rawLine : text.split("\\r?\\n")) {
            if (!rawLine.isBlank()) {
                rows.add(splitLine(rawLine, delimiter));
            }
        }
        if (rows.isEmpty()) {
            throw new StatementFormatException("O arquivo CSV está vazio");
        }
        boolean hasHeader = rows.get(0).stream().noneMatch(CsvStatementParser::looksLikeDate);
        List<String> columns = new ArrayList<>();
        int width = rows.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < width; i++) {
            columns.add(hasHeader && i < rows.get(0).size() && !rows.get(0).get(i).isBlank()
                    ? rows.get(0).get(i).strip()
                    : "Coluna " + (i + 1));
        }
        List<List<String>> data = hasHeader ? rows.subList(1, rows.size()) : rows;
        CsvMapping mapping = requested != null ? requested : detectMapping(columns, data);
        validate(mapping, width);

        List<StatementLine> lines = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        int lineNumber = hasHeader ? 1 : 0;
        for (List<String> row : data) {
            lineNumber++;
            String rawDate = cell(row, mapping.dateColumn());
            String rawAmount = cell(row, mapping.amountColumn());
            if (rawDate.isBlank() && rawAmount.isBlank()) {
                continue;
            }
            // Linhas de saldo ("SALDO ANTERIOR", "Saldo do dia") não são lançamento.
            String description = cell(row, mapping.descriptionColumn()).strip();
            if (DescriptionNormalizer.normalize(description).startsWith("saldo")) {
                continue;
            }
            LocalDate date = parseDate(rawDate, lineNumber);
            BigDecimal amount;
            try {
                amount = Amounts.parse(rawAmount);
            } catch (StatementFormatException e) {
                throw new StatementFormatException("Linha " + lineNumber + ": " + e.getMessage());
            }
            if (mapping.invertSign()) {
                amount = amount.negate();
            }
            if (amount.signum() == 0) {
                continue;
            }
            if (description.isEmpty()) {
                description = "Lançamento importado";
            }
            String key = date + "|" + amount + "|" + DescriptionNormalizer.normalize(description);
            int occurrence = occurrences.merge(key, 1, Integer::sum);
            lines.add(new StatementLine(StatementIds.hash(date, amount, description, occurrence), date, amount,
                    OfxParser.truncate(description.replaceAll("\\s+", " "), 255)));
        }
        if (lines.isEmpty()) {
            throw new StatementFormatException("Nenhum lançamento encontrado no CSV — confira as colunas escolhidas");
        }
        return new Parsed(columns, mapping, lines);
    }

    static char detectDelimiter(String text) {
        String firstLine = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        char best = ';';
        long bestCount = -1;
        for (char candidate : new char[] {';', ',', '\t'}) {
            long count = firstLine.chars().filter(c -> c == candidate).count();
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    // RFC 4180 simplificado: aspas duplas envolvendo o campo, "" como aspa literal.
    static List<String> splitLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static CsvMapping detectMapping(List<String> columns, List<List<String>> data) {
        int date = findColumn(columns, "data", "date", "dt");
        int description = findColumn(columns, "descri", "histor", "lancamento", "estabelecimento", "memo", "title", "titulo");
        int amount = findColumn(columns, "valor", "amount", "value", "quantia", "montante");
        List<String> sample = data.isEmpty() ? List.of() : data.get(0);
        if (date < 0) {
            date = indexWhere(sample, CsvStatementParser::looksLikeDate);
        }
        if (amount < 0) {
            amount = lastIndexWhere(sample, CsvStatementParser::looksLikeAmount);
        }
        if (description < 0) {
            for (int i = 0; i < sample.size(); i++) {
                if (i != date && i != amount) {
                    description = i;
                    break;
                }
            }
        }
        if (date < 0 || amount < 0 || description < 0) {
            throw new StatementFormatException(
                    "Não foi possível identificar as colunas de data, descrição e valor — escolha-as manualmente");
        }
        return new CsvMapping(date, description, amount, false);
    }

    private static void validate(CsvMapping mapping, int width) {
        for (int column : new int[] {mapping.dateColumn(), mapping.descriptionColumn(), mapping.amountColumn()}) {
            if (column < 0 || column >= width) {
                throw new StatementFormatException("Coluna " + (column + 1) + " não existe neste arquivo");
            }
        }
    }

    private static int findColumn(List<String> columns, String... keywords) {
        for (int i = 0; i < columns.size(); i++) {
            String header = Normalizer.normalize(columns.get(i), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (header.contains(keyword)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int indexWhere(List<String> cells, java.util.function.Predicate<String> test) {
        for (int i = 0; i < cells.size(); i++) {
            if (test.test(cells.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexWhere(List<String> cells, java.util.function.Predicate<String> test) {
        for (int i = cells.size() - 1; i >= 0; i--) {
            if (test.test(cells.get(i))) {
                return i;
            }
        }
        return -1;
    }

    static boolean looksLikeDate(String cell) {
        return tryDate(cell.strip()) != null;
    }

    private static boolean looksLikeAmount(String cell) {
        try {
            Amounts.parse(cell);
            return !looksLikeDate(cell);
        } catch (StatementFormatException e) {
            return false;
        }
    }

    private static LocalDate parseDate(String raw, int lineNumber) {
        LocalDate date = tryDate(raw.strip());
        if (date == null) {
            throw new StatementFormatException("Linha " + lineNumber + ": data inválida \"" + raw.strip() + "\"");
        }
        return date;
    }

    private static LocalDate tryDate(String value) {
        String candidate = value.length() > 10 ? value.substring(0, 10) : value;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(candidate, format);
            } catch (DateTimeParseException e) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }
}
