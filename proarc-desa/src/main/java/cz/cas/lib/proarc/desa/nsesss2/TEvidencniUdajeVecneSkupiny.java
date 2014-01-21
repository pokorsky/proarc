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
 * Sada elementů pro evidenční údaje entity "věcná skupina".
 * 
 * <p>Java class for tEvidencniUdajeVecneSkupiny complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tEvidencniUdajeVecneSkupiny">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;group ref="{http://www.mvcr.cz/nsesss/v2}tEntita"/>
 *         &lt;element name="Puvod" type="{http://www.mvcr.cz/nsesss/v2}tPuvodSeskupeni"/>
 *         &lt;element name="Trideni" type="{http://www.mvcr.cz/nsesss/v2}tTrideniVecneSkupiny"/>
 *         &lt;element name="Vyrazovani" type="{http://www.mvcr.cz/nsesss/v2}tVyrazovani"/>
 *         &lt;element name="Manipulace" type="{http://www.mvcr.cz/nsesss/v2}tManipulaceSeskupeni"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tEvidencniUdajeVecneSkupiny", namespace = "http://www.mvcr.cz/nsesss/v2", propOrder = {
    "identifikace",
    "popis",
    "souvislosti",
    "pristupnost",
    "poznamky",
    "jineUdaje",
    "puvod",
    "trideni",
    "vyrazovani",
    "manipulace"
})
public class TEvidencniUdajeVecneSkupiny {

    @XmlElement(name = "Identifikace", namespace = "http://www.mvcr.cz/nsesss/v2", required = true)
    protected TIdentifikace identifikace;
    @XmlElement(name = "Popis", namespace = "http://www.mvcr.cz/nsesss/v2", required = true)
    protected TPopis popis;
    @XmlElement(name = "Souvislosti", namespace = "http://www.mvcr.cz/nsesss/v2")
    protected TSouvislosti souvislosti;
    @XmlElement(name = "Pristupnost", namespace = "http://www.mvcr.cz/nsesss/v2")
    protected TPristupnost pristupnost;
    @XmlElement(name = "Poznamky", namespace = "http://www.mvcr.cz/nsesss/v2")
    protected TPoznamky poznamky;
    @XmlElement(name = "JineUdaje", namespace = "http://www.mvcr.cz/nsesss/v2")
    protected TJineUdaje jineUdaje;
    @XmlElement(name = "Puvod", namespace = "http://www.mvcr.cz/nsesss/v2", required = true)
    protected TPuvodSeskupeni puvod;
    @XmlElement(name = "Trideni", namespace = "http://www.mvcr.cz/nsesss/v2", required = true)
    protected TTrideniVecneSkupiny trideni;
    @XmlElement(name = "Vyrazovani", namespace = "http://www.mvcr.cz/nsesss/v2", required = true)
    protected TVyrazovani vyrazovani;
    @XmlElement(name = "Manipulace", namespace = "http://www.mvcr.cz/nsesss/v2", required = true)
    protected TManipulaceSeskupeni manipulace;

    /**
     * Gets the value of the identifikace property.
     * 
     * @return
     *     possible object is
     *     {@link TIdentifikace }
     *     
     */
    public TIdentifikace getIdentifikace() {
        return identifikace;
    }

    /**
     * Sets the value of the identifikace property.
     * 
     * @param value
     *     allowed object is
     *     {@link TIdentifikace }
     *     
     */
    public void setIdentifikace(TIdentifikace value) {
        this.identifikace = value;
    }

    /**
     * Gets the value of the popis property.
     * 
     * @return
     *     possible object is
     *     {@link TPopis }
     *     
     */
    public TPopis getPopis() {
        return popis;
    }

    /**
     * Sets the value of the popis property.
     * 
     * @param value
     *     allowed object is
     *     {@link TPopis }
     *     
     */
    public void setPopis(TPopis value) {
        this.popis = value;
    }

    /**
     * Gets the value of the souvislosti property.
     * 
     * @return
     *     possible object is
     *     {@link TSouvislosti }
     *     
     */
    public TSouvislosti getSouvislosti() {
        return souvislosti;
    }

    /**
     * Sets the value of the souvislosti property.
     * 
     * @param value
     *     allowed object is
     *     {@link TSouvislosti }
     *     
     */
    public void setSouvislosti(TSouvislosti value) {
        this.souvislosti = value;
    }

    /**
     * Gets the value of the pristupnost property.
     * 
     * @return
     *     possible object is
     *     {@link TPristupnost }
     *     
     */
    public TPristupnost getPristupnost() {
        return pristupnost;
    }

    /**
     * Sets the value of the pristupnost property.
     * 
     * @param value
     *     allowed object is
     *     {@link TPristupnost }
     *     
     */
    public void setPristupnost(TPristupnost value) {
        this.pristupnost = value;
    }

    /**
     * Gets the value of the poznamky property.
     * 
     * @return
     *     possible object is
     *     {@link TPoznamky }
     *     
     */
    public TPoznamky getPoznamky() {
        return poznamky;
    }

    /**
     * Sets the value of the poznamky property.
     * 
     * @param value
     *     allowed object is
     *     {@link TPoznamky }
     *     
     */
    public void setPoznamky(TPoznamky value) {
        this.poznamky = value;
    }

    /**
     * Gets the value of the jineUdaje property.
     * 
     * @return
     *     possible object is
     *     {@link TJineUdaje }
     *     
     */
    public TJineUdaje getJineUdaje() {
        return jineUdaje;
    }

    /**
     * Sets the value of the jineUdaje property.
     * 
     * @param value
     *     allowed object is
     *     {@link TJineUdaje }
     *     
     */
    public void setJineUdaje(TJineUdaje value) {
        this.jineUdaje = value;
    }

    /**
     * Gets the value of the puvod property.
     * 
     * @return
     *     possible object is
     *     {@link TPuvodSeskupeni }
     *     
     */
    public TPuvodSeskupeni getPuvod() {
        return puvod;
    }

    /**
     * Sets the value of the puvod property.
     * 
     * @param value
     *     allowed object is
     *     {@link TPuvodSeskupeni }
     *     
     */
    public void setPuvod(TPuvodSeskupeni value) {
        this.puvod = value;
    }

    /**
     * Gets the value of the trideni property.
     * 
     * @return
     *     possible object is
     *     {@link TTrideniVecneSkupiny }
     *     
     */
    public TTrideniVecneSkupiny getTrideni() {
        return trideni;
    }

    /**
     * Sets the value of the trideni property.
     * 
     * @param value
     *     allowed object is
     *     {@link TTrideniVecneSkupiny }
     *     
     */
    public void setTrideni(TTrideniVecneSkupiny value) {
        this.trideni = value;
    }

    /**
     * Gets the value of the vyrazovani property.
     * 
     * @return
     *     possible object is
     *     {@link TVyrazovani }
     *     
     */
    public TVyrazovani getVyrazovani() {
        return vyrazovani;
    }

    /**
     * Sets the value of the vyrazovani property.
     * 
     * @param value
     *     allowed object is
     *     {@link TVyrazovani }
     *     
     */
    public void setVyrazovani(TVyrazovani value) {
        this.vyrazovani = value;
    }

    /**
     * Gets the value of the manipulace property.
     * 
     * @return
     *     possible object is
     *     {@link TManipulaceSeskupeni }
     *     
     */
    public TManipulaceSeskupeni getManipulace() {
        return manipulace;
    }

    /**
     * Sets the value of the manipulace property.
     * 
     * @param value
     *     allowed object is
     *     {@link TManipulaceSeskupeni }
     *     
     */
    public void setManipulace(TManipulaceSeskupeni value) {
        this.manipulace = value;
    }

}
