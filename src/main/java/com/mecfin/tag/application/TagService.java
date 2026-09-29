package com.mecfin.tag.application;

import com.mecfin.shared.security.CurrentUser;
import com.mecfin.tag.domain.Tag;
import com.mecfin.tag.infra.TagRepository;
import com.mecfin.tag.infra.TagUsageProjection;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    public static final int MAX_TAGS_PER_TRANSACTION = 10;

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional
    public TagView create(String name, String color) {
        UUID householdId = CurrentUser.householdId();
        requireUniqueName(householdId, name, null);
        return new TagView(tagRepository.save(new Tag(householdId, name, color)), 0);
    }

    public List<TagView> list() {
        List<Tag> tags = tagRepository.findAllByHouseholdIdOrderByNameAsc(CurrentUser.householdId());
        if (tags.isEmpty()) {
            return List.of();
        }
        Map<String, Long> usage = tagRepository.countUsage(tags.stream().map(Tag::getId).toList()).stream()
                .collect(Collectors.toMap(TagUsageProjection::getTagId, TagUsageProjection::getTotal));
        return tags.stream()
                .map(tag -> new TagView(tag, usage.getOrDefault(tag.getId().toString(), 0L)))
                .toList();
    }

    public Map<UUID, String> namesById() {
        return tagRepository.findAllByHouseholdIdOrderByNameAsc(CurrentUser.householdId()).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
    }

    @Transactional
    public TagView update(UUID id, String name, String color) {
        Tag tag = getOwnedOrThrow(id);
        requireUniqueName(tag.getHouseholdId(), name, id);
        tag.update(name, color);
        return new TagView(tag, 0);
    }

    // Hard delete: a FK de transaction_tags tem ON DELETE CASCADE - a tag some dos lançamentos,
    // os lançamentos ficam intactos.
    @Transactional
    public void delete(UUID id) {
        tagRepository.delete(getOwnedOrThrow(id));
    }

    /**
     * Valida as tags de um payload de lançamento: todas do household corrente, no máximo
     * {@value #MAX_TAGS_PER_TRANSACTION}. Campo de payload inválido é 400 (mesmo padrão de
     * accountId/categoryId), não 404.
     */
    public Set<UUID> requireOwned(Collection<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> unique = new HashSet<>(tagIds);
        if (unique.size() > MAX_TAGS_PER_TRANSACTION) {
            throw new IllegalArgumentException("Um lançamento aceita no máximo " + MAX_TAGS_PER_TRANSACTION + " tags");
        }
        List<Tag> owned = tagRepository.findAllByIdInAndHouseholdId(unique, CurrentUser.householdId());
        if (owned.size() != unique.size()) {
            throw new IllegalArgumentException("tagIds contém tag inválida ou não visível");
        }
        return unique;
    }

    private void requireUniqueName(UUID householdId, String name, UUID excludeId) {
        if (tagRepository.existsByName(householdId, name.strip(), excludeId)) {
            throw new DuplicateTagException(name.strip());
        }
    }

    private Tag getOwnedOrThrow(UUID id) {
        return tagRepository.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new TagNotFoundException(id));
    }
}
