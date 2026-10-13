package engine.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/** Binds {@code <GM-LMSR>} - carries the liquidity parameter {@code b}. */
@XmlAccessorType(XmlAccessType.FIELD)
public class GmLmsrJaxb {

    @XmlElement(name = "b", required = true)
    private int b;

    public int getB() {
        return b;
    }
}
