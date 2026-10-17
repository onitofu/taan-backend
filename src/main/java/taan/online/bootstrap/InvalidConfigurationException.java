package taan.online.bootstrap;

public final class InvalidConfigurationException extends IllegalArgumentException {
    private final String setting;

    InvalidConfigurationException(String setting, String reason) {
        super(reason + ": " + setting);
        this.setting = setting;
    }

    public String setting() {
        return setting;
    }
}
