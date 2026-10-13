package engine.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Binds {@code <GM-method>} - a choice between an LMSR definition and an
 * order-book definition. Exactly one of the two is present in a valid file.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GmMethodJaxb {

    @XmlElement(name = "GM-LMSR")
    private GmLmsrJaxb lmsr;

    @XmlElement(name = "GM-order-book")
    private GmOrderBookJaxb orderBook;

    public GmLmsrJaxb getLmsr() {
        return lmsr;
    }

    public GmOrderBookJaxb getOrderBook() {
        return orderBook;
    }
}
