//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2014.01.21 at 01:10:09 AM CET 
//


package cz.cas.lib.proarc.desa.nsesss2;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * Metadatový kontejner pro zaznamenání procesu redakce nebo konverze dokumentu.
 * 
 * <p>Java class for tPrevodDokumentu complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tPrevodDokumentu">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Redakce" type="{http://www.mvcr.cz/nsesss/v2}tRedakce" minOccurs="0"/>
 *         &lt;element name="KonverzeAD-DA" type="{http://www.mvcr.cz/nsesss/v2}tKonverzeAD-DA" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tPrevodDokumentu", namespace = "http://www.mvcr.cz/nsesss/v2", propOrder = {
    "redakce",
    "konverzeADDA"
})
public class TPrevodDokumentu {

    @XmlElement(name = "Redakce", namespace = "http://www.mvcr.cz/nsesss/v2")
    protected TRedakce redakce;
    @XmlElement(name = "KonverzeAD-DA", namespace = "http://www.mvcr.cz/nsesss/v2")
    protected TKonverzeADDA konverzeADDA;

    /**
     * Gets the value of the redakce property.
     * 
     * @return
     *     possible object is
     *     {@link TRedakce }
     *     
     */
    public TRedakce getRedakce() {
        return redakce;
    }

    /**
     * Sets the value of the redakce property.
     * 
     * @param value
     *     allowed object is
     *     {@link TRedakce }
     *     
     */
    public void setRedakce(TRedakce value) {
        this.redakce = value;
    }

    /**
     * Gets the value of the konverzeADDA property.
     * 
     * @return
     *     possible object is
     *     {@link TKonverzeADDA }
     *     
     */
    public TKonverzeADDA getKonverzeADDA() {
        return konverzeADDA;
    }

    /**
     * Sets the value of the konverzeADDA property.
     * 
     * @param value
     *     allowed object is
     *     {@link TKonverzeADDA }
     *     
     */
    public void setKonverzeADDA(TKonverzeADDA value) {
        this.konverzeADDA = value;
    }

}
