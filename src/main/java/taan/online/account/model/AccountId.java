package taan.online.account.model;

public record AccountId(long value) {
    public AccountId {
        if (value < 1 || value > 0xffff_ffffL) {
            throw new IllegalArgumentException("Account identity is outside the wire range");
        }
    }
}
