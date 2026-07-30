package com.survey.meetorsolo.domain.matching.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class ArrivalMinutesValidator implements ConstraintValidator<ArrivalMinutes, Integer> {

    private static final Set<Integer> ALLOWED = Set.of(5, 10, 20, 25);

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || ALLOWED.contains(value);
    }
}
