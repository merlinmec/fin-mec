package com.mecfin.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.category.api.CategoryResponse;
import com.mecfin.category.api.CreateCategoryRequest;
import com.mecfin.category.api.UpdateCategoryRequest;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryIT {

    private static final int SYSTEM_DEFAULT_CATEGORY_COUNT = 6;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RestTestClient client;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private AuthenticatedTestUser registerUser() {
        return AuthTestSupport.registerAndLogin(client, uniqueEmail(), "s3cret1234");
    }

    // AuthTestSupport.authenticate() devolve RequestHeadersSpec<?> (sem contentType()/body()),
    // então requisições com corpo montam cookie+header de sessão manualmente, como o AccountIT já faz.
    private EntityExchangeResult<CategoryResponse> createCategory(AuthenticatedTestUser user, String name, UUID parentId) {
        return client.post().uri("/categories")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCategoryRequest(name, CategoryType.EXPENSE, parentId, "#123456", "tag"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult();
    }

    private List<CategoryResponse> listCategories(AuthenticatedTestUser user) {
        return user.authenticate(client.get().uri("/categories"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<CategoryResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    @Test
    void createCategory_returns201ScopedToOwnHousehold() {
        AuthenticatedTestUser user = registerUser();

        EntityExchangeResult<CategoryResponse> result = createCategory(user, "Assinaturas", null);

        CategoryResponse body = result.getResponseBody();
        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo("Assinaturas");
        assertThat(body.type()).isEqualTo(CategoryType.EXPENSE);
        assertThat(body.systemDefault()).isFalse();
    }

    @Test
    void createCategoryWithoutSession_returns401() {
        client.post().uri("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCategoryRequest("Assinaturas", CategoryType.EXPENSE, null, null, null))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listCategories_includesSystemDefaultsAndOwnHouseholdCategoriesOnly() {
        AuthenticatedTestUser owner = registerUser();
        createCategory(owner, "Assinaturas", null);
        AuthenticatedTestUser other = registerUser();
        createCategory(other, "Categoria de Outro Household", null);

        List<CategoryResponse> categories = listCategories(owner);

        assertThat(categories).hasSize(SYSTEM_DEFAULT_CATEGORY_COUNT + 1);
        assertThat(categories).anyMatch(c -> c.name().equals("Moradia") && c.systemDefault());
        assertThat(categories).anyMatch(c -> c.name().equals("Assinaturas") && !c.systemDefault());
        assertThat(categories).noneMatch(c -> c.name().equals("Categoria de Outro Household"));
    }

    @Test
    void getSystemDefaultCategory_isVisibleToAnyHousehold() {
        AuthenticatedTestUser user = registerUser();
        UUID moradiaId = UUID.fromString("a3d1f7c2-1a10-4e8a-9c3b-000000000001");

        user.authenticate(client.get().uri("/categories/" + moradiaId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void getCategoryFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID categoryId = createCategory(owner, "Categoria Privada", null).getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.get().uri("/categories/" + categoryId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createCategory_withInvisibleParentId_returns400() {
        AuthenticatedTestUser user = registerUser();

        client.post().uri("/categories")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCategoryRequest("Sub", CategoryType.EXPENSE, UUID.randomUUID(), null, null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createCategory_withSystemDefaultAsParent_isAllowed() {
        AuthenticatedTestUser user = registerUser();
        UUID moradiaId = UUID.fromString("a3d1f7c2-1a10-4e8a-9c3b-000000000001");

        EntityExchangeResult<CategoryResponse> result = createCategory(user, "Aluguel", moradiaId);

        assertThat(result.getResponseBody().parentId()).isEqualTo(moradiaId);
    }

    @Test
    void updateCategory_changesNameTypeAndColor() {
        AuthenticatedTestUser owner = registerUser();
        UUID categoryId = createCategory(owner, "Categoria Original", null).getResponseBody().id();

        EntityExchangeResult<CategoryResponse> result = client.put().uri("/categories/" + categoryId)
                .cookie("JSESSIONID", owner.sessionCookie())
                .cookie("XSRF-TOKEN", owner.csrfToken())
                .header("X-XSRF-TOKEN", owner.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateCategoryRequest("Categoria Renomeada", CategoryType.INCOME, null, "#654321", "star"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CategoryResponse.class)
                .returnResult();

        CategoryResponse body = result.getResponseBody();
        assertThat(body.name()).isEqualTo("Categoria Renomeada");
        assertThat(body.type()).isEqualTo(CategoryType.INCOME);
        assertThat(body.color()).isEqualTo("#654321");
    }

    @Test
    void updateCategoryFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID categoryId = createCategory(owner, "Categoria Privada", null).getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        client.put().uri("/categories/" + categoryId)
                .cookie("JSESSIONID", intruder.sessionCookie())
                .cookie("XSRF-TOKEN", intruder.csrfToken())
                .header("X-XSRF-TOKEN", intruder.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateCategoryRequest("Hackeada", CategoryType.EXPENSE, null, null, null))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateSystemDefaultCategory_returns404() {
        AuthenticatedTestUser user = registerUser();
        UUID moradiaId = UUID.fromString("a3d1f7c2-1a10-4e8a-9c3b-000000000001");

        client.put().uri("/categories/" + moradiaId)
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateCategoryRequest("Moradia Hackeada", CategoryType.EXPENSE, null, null, null))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCategory_softDeletesAndSubsequentGetReturns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID categoryId = createCategory(owner, "Categoria a Excluir", null).getResponseBody().id();

        owner.authenticate(client.delete().uri("/categories/" + categoryId))
                .exchange()
                .expectStatus().isNoContent();

        owner.authenticate(client.get().uri("/categories/" + categoryId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSystemDefaultCategory_returns404() {
        AuthenticatedTestUser user = registerUser();
        UUID moradiaId = UUID.fromString("a3d1f7c2-1a10-4e8a-9c3b-000000000001");

        user.authenticate(client.delete().uri("/categories/" + moradiaId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCategoryFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID categoryId = createCategory(owner, "Categoria Privada", null).getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.delete().uri("/categories/" + categoryId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }
}
