package engine.core;

/** The two ways an event may charge commission. */
public enum CommissionType {
    ON_PURCHASE("on-purchase"),
    ON_CLOSE("on-close");

    private final String xmlValue;

    CommissionType(String xmlValue) {
        this.xmlValue = xmlValue;
    }

    public String getDisplayName() {
        return xmlValue;
    }

    public static CommissionType fromXmlValue(String value) {
        if (value != null) {
            for (CommissionType type : values()) {
                if (type.xmlValue.equalsIgnoreCase(value.trim())) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("Unknown commission type: '" + value + "'");
    }
}
