//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2014.01.21 at 01:10:09 AM CET 
//


package cz.cas.lib.proarc.desa.nsesss2;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * Metadatový kontejner pro identifikaci jazyků dokumentů.
 * 
 * <p>Java class for tJazyky complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tJazyky">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Jazyk" type="{http://www.mvcr.cz/nsesss/v2}tJazyk" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tJazyky", namespace = "http://www.mvcr.cz/nsesss/v2", propOrder = {
    "jazyk"
})
public class TJazyky {

    @XmlElement(name = "Jazyk", namespace = "http://www.mvcr.cz/nsesss/v2", required = true)
    protected List<String> jazyk;

    /**
     * Gets the value of the jazyk property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the jazyk property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getJazyk().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getJazyk() {
        if (jazyk == null) {
            jazyk = new ArrayList<String>();
        }
        return this.jazyk;
    }

}
