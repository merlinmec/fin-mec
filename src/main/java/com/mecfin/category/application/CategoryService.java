package com.mecfin.category.application;

import com.mecfin.category.domain.Category;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category create(String name, CategoryType type, UUID parentId, String color, String icon) {
        UUID householdId = CurrentUser.householdId();
        validateParent(parentId, null, householdId);
        Category category = new Category(householdId, name, type, parentId, color, icon);
        return categoryRepository.save(category);
    }

    public List<Category> list() {
        return categoryRepository.findAllVisibleToHousehold(CurrentUser.householdId());
    }

    public Category get(UUID id) {
        return categoryRepository.findVisibleByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    @Transactional
    public Category update(UUID id, String name, CategoryType type, UUID parentId, String color, String icon) {
        Category category = getOwnedOrThrow(id);
        validateParent(parentId, id, category.getHouseholdId());
        category.update(name, type, parentId, color, icon);
        return category;
    }

    @Transactional
    public void delete(UUID id) {
        getOwnedOrThrow(id).softDelete();
    }

    // Categorias padrão do sistema (household_id nulo) são visíveis a todo household,
    // mas só posse exclusiva (própria do household) permite update/delete.
    private Category getOwnedOrThrow(UUID id) {
        return categoryRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(id, CurrentUser.householdId())
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private void validateParent(UUID parentId, UUID selfId, UUID householdId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new IllegalArgumentException("Uma categoria não pode ser pai dela mesma");
        }
        categoryRepository.findVisibleByIdAndHouseholdId(parentId, householdId)
                .orElseThrow(() -> new IllegalArgumentException("parentId inválido ou não visível: " + parentId));
    }
}
