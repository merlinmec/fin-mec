package com.mecfin.goal.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class GoalNotFoundException extends NotFoundException {

    public GoalNotFoundException(UUID id) {
        super("Meta não encontrada: " + id);
    }
}
