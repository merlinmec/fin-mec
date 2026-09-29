package com.mecfin.importing.api;

import com.mecfin.importing.application.CsvMapping;
import com.mecfin.importing.application.ImportPreview;
import com.mecfin.importing.application.ImportResult;
import com.mecfin.importing.application.ImportService;
import com.mecfin.importing.application.StatementFormatException;
import com.mecfin.shared.exception.PayloadTooLargeException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importação de extrato (Fase 15). O arquivo só é lido na pré-visualização e descartado — nada
 * de upload persistido. Limites: 2 MB (MAX_BYTES) e 2.000 lançamentos.
 */
@RestController
@RequestMapping("/imports")
public class ImportController {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("ofx", "qfx", "csv", "txt");

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    // Colunas do CSV são opcionais: sem elas o backend detecta; a UI reenvia com a escolha do
    // usuário quando a detecção erra.
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview preview(
            @RequestPart("file") MultipartFile file,
            @RequestParam UUID accountId,
            @RequestParam(required = false) Integer dateColumn,
            @RequestParam(required = false) Integer descriptionColumn,
            @RequestParam(required = false) Integer amountColumn,
            @RequestParam(defaultValue = "false") boolean invertSign) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "extrato" : file.getOriginalFilename();
        String extension = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new StatementFormatException("Formato não suportado — envie o extrato em OFX ou CSV");
        }
        // Limite próprio (o multipart global é 5 MB por causa dos comprovantes, Fase 20).
        if (file.getSize() > MAX_BYTES) {
            throw new PayloadTooLargeException("O extrato pode ter até 2 MB");
        }
        byte[] content = file.getBytes();
        if (content.length == 0) {
            throw new StatementFormatException("O arquivo está vazio");
        }
        if (looksBinary(content)) {
            throw new StatementFormatException("O arquivo não parece ser texto (OFX/CSV) — confira se é o extrato certo");
        }
        CsvMapping mapping = dateColumn != null && descriptionColumn != null && amountColumn != null
                ? new CsvMapping(dateColumn, descriptionColumn, amountColumn, invertSign)
                : null;
        return importService.preview(accountId, fileName, content, mapping);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult commit(@Valid @RequestBody CommitImportRequest request) {
        return importService.commit(request.accountId(), request.fileName(), request.format(),
                request.rows().stream().map(CommitImportRequest.Row::toCommitRow).toList());
    }

    @GetMapping
    public List<ImportBatchResponse> history() {
        return importService.history().stream().map(ImportBatchResponse::from).toList();
    }

    @PostMapping("/{id}/undo")
    public Map<String, Integer> undo(@PathVariable UUID id) {
        return Map.of("canceled", importService.undo(id));
    }

    // Byte nulo nos primeiros 4 KB: PDF, planilha .xlsx, imagem — nunca um OFX/CSV de verdade.
    private static boolean looksBinary(byte[] content) {
        int limit = Math.min(content.length, 4096);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
