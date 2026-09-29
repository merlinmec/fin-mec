package com.mecfin.goal.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class GoalContributionNotFoundException extends NotFoundException {

    public GoalContributionNotFoundException(UUID id) {
        super("Aporte não encontrado: " + id);
    }
}
