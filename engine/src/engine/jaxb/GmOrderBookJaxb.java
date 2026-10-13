package engine.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Binds {@code <GM-order-book>} with its three attributes: the base value
 * {@code d}, the market maker's {@code initial} purchase amount, and the
 * {@code allow-mint} flag.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GmOrderBookJaxb {

    @XmlAttribute(name = "initial", required = true)
    private int initial;

    @XmlAttribute(name = "d", required = true)
    private int d;

    @XmlAttribute(name = "allow-mint", required = true)
    private String allowMint;

    public int getInitial() {
        return initial;
    }

    public int getD() {
        return d;
    }

    public String getAllowMint() {
        return allowMint;
    }
}
