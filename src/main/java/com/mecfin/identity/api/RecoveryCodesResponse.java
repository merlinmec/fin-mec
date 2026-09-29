package com.mecfin.identity.api;

import java.util.List;

// Única vez em que os códigos aparecem em claro - no banco ficam só os hashes.
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
