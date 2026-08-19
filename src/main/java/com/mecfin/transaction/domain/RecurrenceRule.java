package com.mecfin.transaction.domain;

// Só metadado por enquanto (decisão do usuário, 19/08/2026): marca um lançamento como
// recorrente, mas nenhuma ocorrência futura é gerada automaticamente ainda. O motor de
// geração fica para quando Bill (Fase 6) também precisar dele, evita construir 2x.
// Mesmo conjunto de valores usado por Bill no roadmap.
public enum RecurrenceRule {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    BIMONTHLY,
    TRIMONTHLY,
    YEARLY
}
