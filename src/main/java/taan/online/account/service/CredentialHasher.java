package taan.online.account.service;

import taan.online.account.model.CredentialVerifier;
import taan.online.account.service.model.ClientCredential;

public interface CredentialHasher {
    CredentialVerifier hash(ClientCredential credential);
}
