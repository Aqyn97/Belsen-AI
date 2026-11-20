package com.belsenai.coach;

import com.belsenai.analytics.*;
import com.belsenai.habits.HabitDtos;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CoachEvidenceTest {
    DashboardDtos.Dashboard dashboard(List<DashboardDtos.Goal> goals) {
        return new DashboardDtos.Dashboard(new DashboardDtos.Period(LocalDate.of(2026,11,2),"UTC",Instant.parse("2026-11-02T00:00:00Z"),Instant.parse("2026-11-09T00:00:00Z"),Instant.parse("2026-11-05T00:00:00Z"),true,"FROZEN","CLOSED_UNFINALIZED",1),
            new DashboardDtos.Tasks(3,1,2,new BigDecimal("33.3"),null),new HabitDtos.Consistency(1,2,new BigDecimal("50.0"),null),new DashboardDtos.Goals(goals,0,20,goals.size(),null));
    }
    @Test void exactEvidenceDeterministicActionsAndNoSourceMetadata() {
        var source=dashboard(List.of(new DashboardDtos.Goal(UUID.randomUUID(),"secret instruction","CURRENT","secret unit",new BigDecimal("123456789012345678901.1234"),null,null,"NO_WEEKLY_TARGET")));
        var a=CoachEvidence.from(source);assertThat(a).isEqualTo(CoachEvidence.from(source));
        assertThat(a.facts().get(2).actual()).isEqualTo("123456789012345678901.1234");
        assertThat(a.toString()).doesNotContain("secret","CURRENT");
        assertThat(a.period()).isEqualTo(source.period());
        assertThat(a.candidates()).extracting(CoachEvidence.Candidate::action).containsExactly(CoachEvidence.Action.REVIEW_TASKS,CoachEvidence.Action.REVIEW_ROUTINE,CoachEvidence.Action.PLAN_TARGET);
        assertThatThrownBy(()->a.facts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void targetConditionsKeepOverTargetUnits() {
        var g=new DashboardDtos.Goal(UUID.randomUUID(),"x","CURRENT","u",new BigDecimal("12.3456"),BigDecimal.TEN,new BigDecimal("100.0"),null);
        var a=CoachEvidence.from(dashboard(List.of(g)));
        assertThat(a.candidates().get(2).action()).isEqualTo(CoachEvidence.Action.REVIEW_NEXT_PLAN);
        assertThat(a.facts().get(2).actual()).isEqualTo("12.3456");
    }
    @Test void canonicalSelectionNormalizesEquivalentInstantsAndExcludesSubmissionId() {
        var a=new CoachSelection(UUID.randomUUID(),LocalDate.of(2026,11,2),Instant.parse("2026-11-04T12:00:00.000Z"),1);
        var b=new CoachSelection(UUID.randomUUID(),LocalDate.of(2026,11,2),Instant.parse("2026-11-04T12:00:00Z"),1);
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint()).hasSize(64);
        assertThat(new CoachSelection(UUID.randomUUID(),null,null,1).fingerprint()).isNotEqualTo(new CoachSelection(UUID.randomUUID(),LocalDate.of(2026,11,2),null,1).fingerprint());
    }
    @Test void preparationUsesExactlyOneAuthoritativeSnapshot() {
        var dashboard=org.mockito.Mockito.mock(DashboardService.class);
        UUID owner=UUID.randomUUID();var source=dashboard(List.of());
        org.mockito.Mockito.when(dashboard.dashboard(owner,null,null,0,20)).thenReturn(source);
        assertThat(new CoachEvidence(dashboard).prepare(owner,null,null)).isEqualTo(CoachEvidence.from(source));
        org.mockito.Mockito.verify(dashboard).dashboard(owner,null,null,0,20);
        org.mockito.Mockito.verifyNoMoreInteractions(dashboard);
    }

    @Test void serverPresentationSurvivesReplayWithoutEnteringProviderEvidence() {
        var candidate=CoachEvidence.from(dashboard(List.of())).candidates().getFirst();
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        String json=mapper.writeValueAsString(candidate);
        assertThat(json).contains("Review task commitments","Review current tasks");
        assertThat(mapper.readValue(json,CoachEvidence.Candidate.class)).isEqualTo(candidate);
    }

}
