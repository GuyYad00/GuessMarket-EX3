package engine.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

/** Binds {@code <GM-options>} - the (up to two) option names of an event. */
@XmlAccessorType(XmlAccessType.FIELD)
public class GmOptionsJaxb {

    @XmlElement(name = "GM-option")
    private List<String> optionList = new ArrayList<>();

    public List<String> getOptionList() {
        return optionList;
    }
}
