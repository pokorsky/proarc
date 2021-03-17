<?xml version="1.0" encoding="UTF-8"?>

<!--
    Extracts title as plain text
-->

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:mods="http://www.loc.gov/mods/v3">
    <xsl:output method="text"/>

    <xsl:variable name="family" select="//mods:mods/mods:name[@type='personal']/mods:namePart[@type='family']"/>
    <xsl:variable name="given" select="//mods:mods/mods:name[@type='personal']/mods:namePart[@type='given']"/>
    <xsl:variable name="date" select="//mods:mods/mods:name[@type='personal']/mods:namePart[@type='date']"/>
    <xsl:variable name="identifier" select="//mods:mods/mods:name[@type='personal']/mods:nameIdentifier"/>
    <xsl:variable name="namePart" select="//mods:mods/mods:name/mods:namePart"/>
    <xsl:variable name="topicOfSubject" select="//mods:mods/mods:subject/mods:topic"/>
    <xsl:variable name="geographicCode" select="//mods:mods/mods:subject/mods:geographicCode"/>

    <xsl:template match="/">
        <xsl:choose>
            <xsl:when test="boolean(normalize-space($geographicCode))">
                <xsl:value-of select="concat('Geographic code: ', $geographicCode)"/>
            </xsl:when>
            <xsl:when test="boolean(normalize-space($topicOfSubject))">
                <xsl:value-of select="$topicOfSubject"/>
            </xsl:when>
            <xsl:when test="boolean(normalize-space($family))">
                    <xsl:choose>
                        <xsl:when test="boolean(normalize-space($date))">
                            <xsl:value-of select="concat($family, ' ', $given, ' (',$date, '; ',$identifier ,')')"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="concat($family, ' ', $given, ' (',$identifier ,')')"/>
                        </xsl:otherwise>
                    </xsl:choose>
            </xsl:when>
            <xsl:when test="boolean(normalize-space($namePart))">
                <xsl:value-of select="$namePart"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="'--- Nedefinováno ---'"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>