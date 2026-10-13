package engine.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Binds a single {@code <GM-event>} element of the exercise-3 schema.
 * <p>
 * There is no {@code id} element any more: events are identified by their
 * unique name, and the server assigns a running internal id when the event is
 * added to the system.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GmEventJaxb {

    @XmlAttribute(name = "name", required = true)
    private String name;

    @XmlElement(name = "description", required = true)
    private String description;

    @XmlElement(name = "commission", required = true)
    private CommissionJaxb commission;

    @XmlElement(name = "GM-options", required = true)
    private GmOptionsJaxb options;

    @XmlElement(name = "GM-method", required = true)
    private GmMethodJaxb method;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CommissionJaxb getCommission() {
        return commission;
    }

    public GmOptionsJaxb getOptions() {
        return options;
    }

    public GmMethodJaxb getMethod() {
        return method;
    }
}
