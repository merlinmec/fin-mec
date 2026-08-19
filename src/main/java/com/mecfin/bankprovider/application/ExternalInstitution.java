package com.mecfin.bankprovider.application;

// Retorno de BankProviderClient.listInstitutions() - uma instituição do catálogo do provedor,
// ainda não persistida como BankInstitution (quem decide persistir/atualizar é o chamador da
// porta, não o adapter).
public record ExternalInstitution(String providerCode, String name) {
}
