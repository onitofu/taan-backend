package taan.online.account.protocol.packet;

public sealed interface AccountRequest permits PqCheckAccount, PqCreateAccount {
    int selector();
}
