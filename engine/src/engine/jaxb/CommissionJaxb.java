package engine.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlValue;

/**
 * Binds {@code <commission type="...">percent</commission>}: an integer
 * percentage with a required {@code type} attribute (on-close / on-purchase).
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class CommissionJaxb {

    @XmlValue
    private int percentage;

    @XmlAttribute(name = "type", required = true)
    private String type;

    public int getPercentage() {
        return percentage;
    }

    public String getType() {
        return type;
    }
}
