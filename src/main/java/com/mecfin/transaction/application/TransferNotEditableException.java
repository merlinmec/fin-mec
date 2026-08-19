package com.mecfin.transaction.application;

import com.mecfin.shared.exception.ConflictException;
import java.util.UUID;

// Editar uma perna de transferência sem a outra quebraria o pareamento (valores
// dessincronizados entre as duas contas). Cancelar e criar de novo é o caminho seguro.
public class TransferNotEditableException extends ConflictException {

    public TransferNotEditableException(UUID id) {
        super("Transferência não pode ser editada diretamente (" + id
                + ") - cancele e crie uma nova em /transactions/transfers");
    }
}
