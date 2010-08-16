package com.sfs.jbtimporter;

import org.apache.commons.lang.StringUtils;

/**
 * The Class JBTIssue.
 */
public class JBTIssue {

    /** The id. */
    private String id;
    
    /** The base. */
    private String base;
    
    /** The file name. */
    private String fileName;
    
    /** The export base. */
    private String exportBase = "";
    
    /**
     * Instantiates a new jBT issue.
     *
     * @param jbt the jbt
     */
    public JBTIssue(final JBTProcessor jbt) {
        if (StringUtils.isNotBlank(jbt.getExportBase())) {
            this.exportBase = jbt.getExportBase();
        }
    }
    
    /**
     * Sets the id.
     *
     * @param idValue the new id
     */
    public final void setId(final String idValue) {
        this.id = idValue;
    }
    
    /**
     * Gets the id.
     *
     * @return the id
     */
    public final String getId() {
        if (this.id == null) {
            this.id = "";
        }
        return this.id;
    }
    
    /**
     * Sets the base.
     *
     * @param baseValue the new base
     */
    public final void setBase(final String baseValue) {
        this.base = baseValue;
    }
    
    /**
     * Gets the base.
     *
     * @return the base
     */
    public final String getBase() {
        if (this.base == null) {
            this.base = "";
        }
        return this.base;
    }
    
    /**
     * Sets the file name.
     *
     * @param fileNameValue the new file name
     */
    public final void setFileName(final String fileNameValue) {
        this.fileName = fileNameValue;
    }
    
    /**
     * Gets the file name.
     *
     * @return the file name
     */
    public final String getFileName() {
        if (this.fileName == null) {
            this.fileName = "";
        }
        return this.fileName;
    }
    
    /**
     * Gets the full file name.
     *
     * @return the full file name
     */
    public final String getFullFileName() {
        
        return StringUtils.replace(this.exportBase + this.getBase(), "\\", "/") 
                + "/" + this.getFileName();
    }
}
