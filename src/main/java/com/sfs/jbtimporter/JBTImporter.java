/*******************************************************************************
 * Copyright 2010 David Harrison.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.sfs.jbtimporter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

/**
 * The Class JBTImporter.
 */
public class JBTImporter {
    
    /**
     * Instantiates a new JBTImporter.
     */
    protected JBTImporter() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * The main method.
     *
     * @param args the arguments
     */
    public static void main(final String[] args) {

        System.out.println();
        System.out.println("--------------------------------");
        System.out.println("| Jira BugTrack issue importer |");
        System.out.println("--------------------------------");
        System.out.println();
        
        JBTProcessor jbt = null;
        try {
            jbt = processArguments(args);
        } catch (JBTException jbte) {
            System.out.println("ERROR: " + jbte.getMessage());            
        }
        
        if (jbt != null) {
            if (jbt.getRevert()) {
                // Revert the transformed issues
                revertTransformation(jbt);
            } else {
                if (StringUtils.isBlank(jbt.getXsltFileName())) {
                    // Import the issues into Jira
                    performImport(jbt);
                } else {
                    // An XSLT transformation has been requested
                    performTransformation(jbt);
                }
            }            
        } else {
            // Print the usage
            System.out.println("Usage (import): -u=username -p=password -h=jira_base_url "
                    + "-d=bugtrack_export_directory");
            System.out.println("Usage (transform): -x=xslt_filename "
                    + "-d=bugtrack_export_directory");
            System.out.println("Usage (revert): -d=bugtrack_export_directory -r");
        }
        System.out.println();
    }
    
    
    /**
     * Perform an import.
     *
     * @param jbt the jbt
     */
    private static void performImport(final JBTProcessor jbt) {
        
        System.out.println("Beginning export...");
        System.out.println("Jira host: " + jbt.getBaseUrl());
        System.out.println("Export directory: " + jbt.getExportBase());
        
        List<JBTIssue> issues = new ArrayList<JBTIssue>();
        try {
            issues = jbt.parseXmlIndex();
        } catch (IOException ioe) {
            System.out.println("ERROR loading index.xml file: " + ioe.getMessage());                
        } catch (SAXException se) {
            System.out.println("ERROR parsing index.xml file: " + se.getMessage());
        }
        // Process the issues in the XML file
        processIssues(jbt, issues);
    }
    
    
    /**
     * Perform a transformation.
     *
     * @param jbt the jbt
     */
    private static void performTransformation(final JBTProcessor jbt) {
        
        System.out.println("Beginning transformation...");
        System.out.println("XSLT file: " + jbt.getXsltFileName());
        System.out.println("Export directory: " + jbt.getExportBase());
        
        List<JBTIssue> issues = new ArrayList<JBTIssue>();
        try {
            issues = jbt.parseXmlIndex();
        } catch (IOException ioe) {
            System.out.println("ERROR loading index.xml file: " + ioe.getMessage());                
        } catch (SAXException se) {
            System.out.println("ERROR parsing index.xml file: " + se.getMessage());
        }
        // Process the issues in the XML file
        transformIssues(jbt, issues);
    }
    
    /**
     * Process the issues that require importing.
     *
     * @param jbt the jbt processor
     * @param issues the issues
     */
    private static void processIssues(final JBTProcessor jbt, 
            final List<JBTIssue> issues) {
        
        int successCount = 0;
        List<String> transitionErrors = new ArrayList<String>();
        List<String> fileErrors = new ArrayList<String>();
        List<String> errors = new ArrayList<String>();
        
        for (JBTIssue issue : issues) {
            
            String error = "";            
            String xmldata = "";      
            try {
                xmldata = jbt.loadXmlDataFile(issue.getFullFileName());
            } catch (IOException ioe) {
                error = "ERROR loading XML: " + ioe.getMessage();
            }
                
            if (StringUtils.isNotBlank(xmldata)) {
                // Import the XML data into Jira
                // Get a valid key
                String key = "";
                try {
                    key = jbt.getKey();
                } catch (IOException ioe) {
                    error = "ERROR getting security key: " + ioe.getMessage();
                }
                if (StringUtils.isNotBlank(key)) {
                    try {                        
                        final String result = jbt.importXML(key, xmldata);
                        // If the result is long then an error was thrown
                        if (result.length() > 30) {
                            error = result;
                        }
                    } catch (IOException ioe) {
                        error = "ERROR communicating with Jira: " + ioe.getMessage();
                    }
                } else {
                    error = "ERROR: The security key is not valid";
                }                
            } else {
                error = "ERROR: The file was empty";
            }
            
            if (StringUtils.isNotBlank(error)) {
                if (error.contains("Unable to make temporary copy of file")) {
                    // File attachment error
                    fileErrors.add(issue.getId());
                }
                if (error.contains("that is not a valid workflow transition for the")) {
                    // Issue transition error
                    transitionErrors.add(issue.getId());
                }

                // There was an issue processing this issue
                errors.add(issue.getId());
            
                System.out.println("Error processing Issue ID: " + issue.getId());
                System.out.println("Filename: " + issue.getFullFileName());                
                System.out.println(error);                
                System.out.println("-------------------------------------");
            }  else {
                successCount++;
            }
        }
        System.out.println();
        System.out.println("=====================================");
        System.out.println("Import complete.");
        System.out.println(successCount + " imported cleanly");
        System.out.println(fileErrors.size() + " imported with file attachment errors");
        System.out.println(transitionErrors.size() + " imported with transition errors");
        System.out.println(errors.size() + " failed due to errors");
        System.out.println("=====================================");
        
        if (fileErrors.size() > 0) {
            System.out.println("Issues with file attachment problems: ");
            for (String id : fileErrors) {
                System.out.print(id);
                System.out.print(", ");
            }
        }
        if (transitionErrors.size() > 0) {
            System.out.println("Issues with transition problems: ");
            for (String id : transitionErrors) {
                System.out.print(id);
                System.out.print(", ");
            }
        }
        if (errors.size() > 0) {
            System.out.println("Issues with serious errors: ");
            for (String id : errors) {
                System.out.print(id);
                System.out.print(", ");
            }
        }
        System.out.println("=====================================");
    }
    
    /**
     * Revert the transformed issue XML files.
     *
     * @param jbt the jbt
     */
    private static void revertTransformation(final JBTProcessor jbt) {
        
        System.out.println("Reverting transformation...");
        System.out.println("Export directory: " + jbt.getExportBase());
        
        List<JBTIssue> issues = new ArrayList<JBTIssue>();
        try {
            issues = jbt.parseXmlIndex();
        } catch (IOException ioe) {
            System.out.println("ERROR loading index.xml file: " + ioe.getMessage());                
        } catch (SAXException se) {
            System.out.println("ERROR parsing index.xml file: " + se.getMessage());
        }
        // Process the issues in the XML file
        revertIssues(jbt, issues);
    }
    
    
    /**
     * Transform the issues to the new XML format.
     *
     * @param jbt the jbt processor
     * @param issues the issues
     */
    private static void transformIssues(final JBTProcessor jbt, 
            final List<JBTIssue> issues) {
        
        final File xsltFile = new File(jbt.getXsltFileName());

        final Source xsltSource = new StreamSource(xsltFile);
        final TransformerFactory transFact = TransformerFactory.newInstance();
        Transformer trans = null;
        
        try {
            final Templates cachedXSLT = transFact.newTemplates(xsltSource);
            trans = cachedXSLT.newTransformer();
        } catch (TransformerConfigurationException tce) {
            System.out.println("ERROR configuring XSLT engine: " + tce.getMessage());
        }
        // Enable indenting and UTF8 encoding
        trans.setOutputProperty(OutputKeys.INDENT, "yes");
        trans.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        
        if (trans != null) {
            for (JBTIssue issue : issues) {
                System.out.println("Processing Issue ID: " + issue.getId());
                System.out.println("Filename: " + issue.getFullFileName());
                     
                // Read the XML file
                final File xmlFile = new File(issue.getFullFileName());
                final File tempFile = new File(issue.getFullFileName() + ".tmp");
                final File originalFile = new File(issue.getFullFileName() + ".old");
                
                Source xmlSource = null;
                if (originalFile.exists()) {
                    // The original file exists, use that as the XML source
                    xmlSource = new StreamSource(originalFile);
                } else {
                    // No backup exists, use the .xml file.
                    xmlSource = new StreamSource(xmlFile);
                }
                
                // Transform the XML file
                try {
                    trans.transform(xmlSource, new StreamResult(tempFile));
                    
                    if (originalFile.exists()) {
                        // Delete the .xml file as it needs to be replaced
                        xmlFile.delete();                        
                    } else {
                        // Rename the existing file with the .old extension
                        xmlFile.renameTo(originalFile);                        
                    }                    
                } catch (TransformerException te) {
                    System.out.println("ERROR transforming XML: " + te.getMessage());
                }
                
                // Read the xmlFile and convert the special characters
                
                OutputStreamWriter out = null;
                try {
                    
                    final BufferedReader in = new BufferedReader(new InputStreamReader(
                            new FileInputStream(tempFile), "UTF8"));
                    
                    out = new OutputStreamWriter(new FileOutputStream(xmlFile), "UTF-8");
                                        
                    int ch = -1;
                    ch = in.read();
                    while (ch != -1) {
                        final char c = (char) ch;

                        if (jbt.getSpecialCharacterMap().containsKey(c)) {
                            // System.out.println("Replacing character: " + c 
                            //        + ", " + jbt.getSpecialCharacterMap().get(c));
                            out.write(jbt.getSpecialCharacterMap().get(c)); 
                        } else {
                            out.write(c);
                        }
                        ch = in.read();
                    }                  
                } catch (IOException ie) {
                    System.out.println("ERROR converting special characters: " 
                            + ie.getMessage());                    
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch ( IOException ie) {
                        System.out.println("ERROR closing the XML file: " 
                                + ie.getMessage());
                    }
                    // Delete the temporary file
                    tempFile.delete();
                }                                
                
                System.out.println("-------------------------------------");
            }
        }
    }
    
    
    /**
     * Revert the issues to the old format.
     *
     * @param jbt the jbt processor
     * @param issues the issues
     */
    private static void revertIssues(final JBTProcessor jbt, 
            final List<JBTIssue> issues) {
        
        for (JBTIssue issue : issues) {
            System.out.println("Reverting Issue ID: " + issue.getId());
            System.out.println("Filename: " + issue.getFullFileName());
                 
            // Read the XML file
            final File xmlFile = new File(issue.getFullFileName());
            final File originalFile = new File(issue.getFullFileName() + ".old");
                                        
            if (originalFile.exists()) {
                // Rename the old file to the original file
                originalFile.renameTo(xmlFile);                      
            }
            System.out.println("-------------------------------------");
        }
    }
    
    
    /**
     * Process the supplied arguments.
     *
     * @param args the args
     * @return the jBT processor
     * @throws JBTException the jBT exception
     */
    private static JBTProcessor processArguments(final String[] args) 
            throws JBTException {
        
        String username = "";
        String password = "";
        String baseUrl = "";
        String exportBase = "";
        String xsltFilename = "";
        boolean revert = false;
        
        for (String s : args) {
            
            if (s.startsWith("-u=")) {
                // Username set
                username = s.substring(s.indexOf("=") + 1, s.length());
            }            
            if (s.startsWith("-p=")) {
                // Password set
                password = s.substring(s.indexOf("=") + 1, s.length());
            }            
            if (s.startsWith("-h=")) {
                // Base url set
                baseUrl = s.substring(s.indexOf("=") + 1, s.length());
            }
            if (s.startsWith("-d=")) {
                // Export directory set
                exportBase = s.substring(s.indexOf("=") + 1, s.length());
            }
            if (s.startsWith("-x=")) {
                // XSLT filename set
                xsltFilename = s.substring(s.indexOf("=") + 1, s.length());
            }
            if (s.startsWith("-r")) {
                // Revert the transformed XML to the originals
                revert = true;
            }
        }
        
        return new JBTProcessor(username, password, baseUrl, exportBase,
                xsltFilename, revert);
    }
}
