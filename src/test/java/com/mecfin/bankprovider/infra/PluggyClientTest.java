package com.mecfin.bankprovider.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mecfin.bankprovider.application.ExternalItem.Health;
import com.mecfin.bankprovider.infra.PluggyClient.PluggyItem;
import org.junit.jupiter.api.Test;

class PluggyClientTest {

    private final PluggyClient client = new PluggyClient("https://api.pluggy.ai/", "id", "secret");

    @Test
    void cursorInEveryFormatStaysOnThePluggyHost() {
        assertThat(client.nextPage("?accountId=a1&after=abc", "a1"))
                .hasToString("https://api.pluggy.ai/v2/transactions?accountId=a1&after=abc");
        assertThat(client.nextPage("/v2/transactions?accountId=a1&after=abc", "a1"))
                .hasToString("https://api.pluggy.ai/v2/transactions?accountId=a1&after=abc");
        assertThat(client.nextPage("https://api.pluggy.ai/v2/transactions?after=abc", "a1"))
                .hasToString("https://api.pluggy.ai/v2/transactions?after=abc");
        // token puro: codificado uma vez só
        assertThat(client.nextPage("eyJ0+/=", "a1").toString())
                .startsWith("https://api.pluggy.ai/v2/transactions?")
                .contains("after=eyJ0%2B%2F%3D");
        assertThat(client.nextPage(null, "a1")).isNull();
        assertThat(client.nextPage(" ", "a1")).isNull();
    }

    @Test
    void cursorPointingToAnotherHostIsRefused() {
        assertThatThrownBy(() -> client.nextPage("https://evil.example/v2/transactions", "a1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.nextPage("https://api.pluggy.ai.evil.example/x", "a1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void itemIdIsValidatedBeforeAnyNetworkCall() {
        assertThatThrownBy(() -> client.getItem("../auth")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.deleteItem("{x}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemStatusMapsToHealth() {
        assertThat(PluggyClient.health(item("UPDATED", "SUCCESS"))).isEqualTo(Health.OK);
        assertThat(PluggyClient.health(item("UPDATING", null))).isEqualTo(Health.OK);
        assertThat(PluggyClient.health(item("LOGIN_ERROR", "INVALID_CREDENTIALS"))).isEqualTo(Health.NEEDS_RECONNECT);
        assertThat(PluggyClient.health(item("WAITING_USER_INPUT", null))).isEqualTo(Health.NEEDS_RECONNECT);
        assertThat(PluggyClient.health(item("OUTDATED", "USER_AUTHORIZATION_REVOKED")))
                .isEqualTo(Health.NEEDS_RECONNECT);
        assertThat(PluggyClient.health(item("OUTDATED", "SITE_NOT_AVAILABLE"))).isEqualTo(Health.ERROR);
    }

    private static PluggyItem item(String status, String execution) {
        return new PluggyItem("i1", status, execution, "h1", null, null);
    }
}
