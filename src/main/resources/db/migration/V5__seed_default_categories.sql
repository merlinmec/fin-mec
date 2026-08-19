-- Categorias padrão do sistema (household_id NULL, visíveis a todo household),
-- replicando o conjunto comum do Organizze conforme roadmap da Fase 3.
INSERT INTO categories (id, household_id, name, type, color, icon, created_at, updated_at) VALUES
    ('a3d1f7c2-1a10-4e8a-9c3b-000000000001', NULL, 'Moradia', 'EXPENSE', '#8B5CF6', 'home', now(), now()),
    ('a3d1f7c2-1a10-4e8a-9c3b-000000000002', NULL, 'Alimentação', 'EXPENSE', '#F59E0B', 'utensils', now(), now()),
    ('a3d1f7c2-1a10-4e8a-9c3b-000000000003', NULL, 'Transporte', 'EXPENSE', '#3B82F6', 'car', now(), now()),
    ('a3d1f7c2-1a10-4e8a-9c3b-000000000004', NULL, 'Saúde', 'EXPENSE', '#EF4444', 'heart-pulse', now(), now()),
    ('a3d1f7c2-1a10-4e8a-9c3b-000000000005', NULL, 'Educação', 'EXPENSE', '#10B981', 'graduation-cap', now(), now()),
    ('a3d1f7c2-1a10-4e8a-9c3b-000000000006', NULL, 'Lazer', 'EXPENSE', '#EC4899', 'popcorn', now(), now());
