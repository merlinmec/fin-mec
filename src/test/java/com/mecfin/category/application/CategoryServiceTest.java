package com.mecfin.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mecfin.category.domain.Category;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.security.AuthenticatedPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private record TestPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    @Mock
    private CategoryRepository categoryRepository;

    private final UUID householdId = UUID.randomUUID();

    @BeforeEach
    void authenticateAsHousehold() {
        AuthenticatedPrincipal principal = new TestPrincipal(UUID.randomUUID(), householdId);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createScopesNewCategoryToCurrentHousehold() {
        CategoryService service = new CategoryService(categoryRepository);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category category = service.create("Assinaturas", CategoryType.EXPENSE, null, "#123456", "tv");

        assertThat(category.getHouseholdId()).isEqualTo(householdId);
        assertThat(category.getName()).isEqualTo("Assinaturas");
        assertThat(category.isSystemDefault()).isFalse();
    }

    @Test
    void createWithInvisibleParentThrows() {
        CategoryService service = new CategoryService(categoryRepository);
        UUID parentId = UUID.randomUUID();
        when(categoryRepository.findVisibleByIdAndHouseholdId(parentId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("Sub", CategoryType.EXPENSE, parentId, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listReturnsCategoriesVisibleToCurrentHousehold() {
        CategoryService service = new CategoryService(categoryRepository);
        Category category = new Category(householdId, "Assinaturas", CategoryType.EXPENSE, null, null, null);
        when(categoryRepository.findAllVisibleToHousehold(householdId)).thenReturn(List.of(category));

        List<Category> categories = service.list();

        assertThat(categories).containsExactly(category);
    }

    @Test
    void getThrowsCategoryNotFoundWhenAbsentOrNotVisible() {
        CategoryService service = new CategoryService(categoryRepository);
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(categoryId)).isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void updateThrowsCategoryNotFoundForSystemDefaultCategory() {
        CategoryService service = new CategoryService(categoryRepository);
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(categoryId, householdId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.update(categoryId, "Nova", CategoryType.EXPENSE, null, null, null))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void updateWithSelfAsParentThrows() {
        CategoryService service = new CategoryService(categoryRepository);
        UUID categoryId = UUID.randomUUID();
        Category category = new Category(householdId, "Lazer", CategoryType.EXPENSE, null, null, null);
        when(categoryRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(categoryId, householdId))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(
                () -> service.update(categoryId, "Lazer", CategoryType.EXPENSE, categoryId, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteSoftDeletesOwnCategory() {
        CategoryService service = new CategoryService(categoryRepository);
        UUID categoryId = UUID.randomUUID();
        Category category = new Category(householdId, "Lazer", CategoryType.EXPENSE, null, null, null);
        when(categoryRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(categoryId, householdId))
                .thenReturn(Optional.of(category));

        service.delete(categoryId);

        assertThat(category.getDeletedAt()).isNotNull();
    }
}
