package engine.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Binds the document root {@code <Guess-Market>} of the exercise-3 schema
 * ({@code GM-EX3-Schema.xsd}).
 * <p>
 * Compared with exercise 2 the file no longer carries a {@code GM-users}
 * section: users register themselves through the login screen, and the user
 * who uploads the file becomes the market maker of every event in it.
 */
@XmlRootElement(name = "Guess-Market")
@XmlAccessorType(XmlAccessType.FIELD)
public class GuessMarketJaxb {

    @XmlElement(name = "GM-events", required = true)
    private GmEventsJaxb events;

    public GmEventsJaxb getEvents() {
        return events;
    }
}
