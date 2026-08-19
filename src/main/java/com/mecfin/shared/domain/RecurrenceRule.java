package com.mecfin.shared.domain;

// Só metadado por enquanto (decisão do usuário, 19/08/2026, reafirmada na Fase 6 quando Bill
// passou a precisar do mesmo conceito): marca um lançamento/conta a pagar como recorrente, mas
// nenhuma ocorrência futura é gerada automaticamente ainda - motor de geração fica pra quando
// isso virar prioridade real. Vive em shared.domain (não em transaction.domain) porque é
// vocabulário compartilhado entre Transaction e Bill, não uma regra de um agregado específico.
public enum RecurrenceRule {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    BIMONTHLY,
    TRIMONTHLY,
    YEARLY
}
