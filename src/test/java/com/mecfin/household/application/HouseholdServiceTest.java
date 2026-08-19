package com.mecfin.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mecfin.household.domain.Household;
import com.mecfin.household.domain.HouseholdMember;
import com.mecfin.household.domain.HouseholdRole;
import com.mecfin.household.infra.HouseholdMemberRepository;
import com.mecfin.household.infra.HouseholdRepository;
import com.mecfin.identity.domain.UserRegisteredEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Test
    void onUserRegisteredCreatesHouseholdWithOwnerMembership() {
        HouseholdService service = new HouseholdService(householdRepository, householdMemberRepository);
        UUID userId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        Household household = new Household("Financeiro de user@example.com");
        ReflectionTestUtils.setField(household, "id", householdId); // simula o id gerado pelo save() real
        when(householdRepository.save(any(Household.class))).thenReturn(household);

        service.onUserRegistered(new UserRegisteredEvent(userId, "user@example.com"));

        ArgumentCaptor<Household> householdCaptor = ArgumentCaptor.forClass(Household.class);
        verify(householdRepository).save(householdCaptor.capture());
        assertThat(householdCaptor.getValue().getName()).isEqualTo("Financeiro de user@example.com");

        ArgumentCaptor<HouseholdMember> memberCaptor = ArgumentCaptor.forClass(HouseholdMember.class);
        verify(householdMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(HouseholdRole.OWNER);
        assertThat(memberCaptor.getValue().getHouseholdId()).isEqualTo(householdId);
    }
}
