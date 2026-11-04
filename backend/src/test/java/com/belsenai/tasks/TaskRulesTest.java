package com.belsenai.tasks;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TaskRulesTest {
    @Test void dueDateAndDurationAreOptional() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new TaskDtos.Create("Read",null,null,null,null))).isEmpty();
        }
    }
    @Test void durationBoundsAndTitleAreValidated() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for(int duration:new int[]{0,-1,1441}) {
                assertThat(validator.validate(new TaskDtos.Create("Read",null,null,null,duration))).isNotEmpty();
            }
            assertThat(validator.validate(new TaskDtos.Create(" ",null,null,null,30))).isNotEmpty();
            assertThat(validator.validate(new TaskDtos.Create("Read",null,null,null,1440))).isEmpty();
        }
    }
    @Test void expectedVersionIsRequiredAndNonNegative() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertThat(validator.validate(new TaskDtos.Version(null))).isNotEmpty();
            assertThat(validator.validate(new TaskDtos.Version(-1L))).isNotEmpty();
            assertThat(validator.validate(new TaskDtos.Version(0L))).isEmpty();
        }
    }
}
