package cr.craftec.crystalpledge.rpbuilder;

public enum WarningType {
    INVALID("Invalid "),
    MISSING("Missing "),
    DELETE("Failed to delete "),
    LANG("Duplicate lang key "),
    CHAR("Duplicate char "),
    SOUND("Duplicate sound "),
    FILE("Duplicate file "),
    ;

    private final String message;

    WarningType(String message) { this.message = message; }

    public String getMessage() { return message; }
}
