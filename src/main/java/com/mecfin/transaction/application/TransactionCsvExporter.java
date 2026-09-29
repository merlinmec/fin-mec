package com.mecfin.transaction.application;

import com.mecfin.account.application.AccountService;
import com.mecfin.category.domain.Category;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionDirection;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exporta lançamentos filtrados em CSV (pt-BR). Valor com sinal (despesa e saída de
 * transferência negativas) para que somar a coluna no Excel dê o fluxo líquido do período.
 */
@Service
public class TransactionCsvExporter {

    public static final int MAX_ROWS = 10_000;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final String HEADER = "Data;Competência;Descrição;Categoria;Conta;Tipo;Situação;Valor";

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryRepository categoryRepository;

    public TransactionCsvExporter(
            TransactionService transactionService,
            AccountService accountService,
            CategoryRepository categoryRepository) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public byte[] export(TransactionFilter filter) {
        Page<Transaction> page = transactionService.search(filter, PageRequest.of(0, MAX_ROWS));
        if (page.getTotalElements() > MAX_ROWS) {
            throw new IllegalArgumentException("A exportação é limitada a " + MAX_ROWS
                    + " lançamentos - refine os filtros (ex.: um intervalo de datas menor)");
        }
        Map<UUID, String> accountNames = accountService.householdAccountNames();
        Set<UUID> categoryIds = page.stream().map(Transaction::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> categoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        DecimalFormat money = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (Transaction t : page) {
            csv.append(String.join(";",
                    t.getTransactionDate().format(DATE),
                    t.getCompetenceMonth().format(MONTH),
                    cell(t.getDescription()),
                    cell(lookup(categoryNames, t.getCategoryId())),
                    cell(lookup(accountNames, t.getAccountId())),
                    typeLabel(t),
                    statusLabel(t.getStatus()),
                    money.format(signed(t))))
                    .append("\r\n");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * Célula de texto livre: entre aspas (com aspas internas dobradas) e neutralizada contra
     * CSV/formula injection - uma descrição começando com =, +, -, @, tab ou CR seria executada
     * como fórmula pelo Excel/LibreOffice ao abrir o arquivo (OWASP "CSV Injection").
     */
    static String cell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String lookup(Map<UUID, String> names, UUID id) {
        return id == null ? "" : names.getOrDefault(id, "");
    }

    private static BigDecimal signed(Transaction t) {
        boolean outflow = t.getType() == TransactionType.EXPENSE
                || t.getTransferDirection() == TransactionDirection.OUT;
        return outflow ? t.getAmount().negate() : t.getAmount();
    }

    private static String typeLabel(Transaction t) {
        return switch (t.getType()) {
            case INCOME -> "Receita";
            case EXPENSE -> "Despesa";
            case TRANSFER -> t.getTransferDirection() == TransactionDirection.OUT
                    ? "Transferência (saída)"
                    : "Transferência (entrada)";
        };
    }

    private static String statusLabel(TransactionStatus status) {
        return switch (status) {
            case POSTED -> "Efetivado";
            case PENDING -> "Previsto";
            case CANCELED -> "Cancelado";
        };
    }
}
