/*
 * 
 */
package com.sfs.jbtimporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The Class JBTProcessor.
 */
public class JBTProcessor {

    /** The username. */
    private String username;

    /** The password. */
    private String password;

    /** The base url. */
    private String baseUrl = "http://localhost:8080/";

    /** The export base. */
    private String exportBase;
    
    /** The xslt filename. */
    private String xsltFileName;
    
    /** The revert flag. */
    private boolean revert = false;

    /** The http client. */
    private HttpClient httpClient;

    /** The jira key path. */
    private final String jiraKeyPath = "secure/admin/util/JellyRunner!default.jspa";

    /** The jira runner path. */
    private final String jiraRunnerPath = "secure/admin/util/JellyRunner.jspa";

    /**
     * Instantiates a new jBT processor.
     * 
     * @param usernameValue the username
     * @param passwordValue the password
     * @param baseUrlValue the base url
     * @param exportBaseValue the export base
     * @param xsltFileNameValue the xslt file name
     * @param revertValue the revert flag
     * @throws JBTException the jBT exception
     */
    public JBTProcessor(final String usernameValue, final String passwordValue,
            final String baseUrlValue, final String exportBaseValue,
            final String xsltFileNameValue, final boolean revertValue)
            throws JBTException {

        if (StringUtils.isBlank(exportBaseValue)) {
            throw new JBTException("A valid export directory is required");
        }
        // If the XSLT filename is not supplied and revert flag not set.
        // Check to see if a user and password has been defined.
        if (StringUtils.isNotBlank(xsltFileNameValue) || revertValue == true) {
            // In these two cases no further validation needs to be performed
        } else {
            if (StringUtils.isBlank(usernameValue)) {
                throw new JBTException("A valid username is required");
            }
            if (StringUtils.isBlank(passwordValue)) {
                throw new JBTException("A valid password is required");
            }
        }

        // Set the configuration parameters
        this.username = usernameValue;
        this.password = passwordValue;
        this.xsltFileName = xsltFileNameValue;
        this.revert = revertValue;
        if (StringUtils.isNotBlank(exportBaseValue)) {
            if (exportBaseValue.endsWith("/")) {
                this.exportBase = exportBaseValue;
            } else {
                this.exportBase = exportBaseValue + "/";
            }
        }
        if (StringUtils.isNotBlank(baseUrlValue)) {
            if (baseUrlValue.endsWith("/")) {
                this.baseUrl = baseUrlValue;
            } else {
                this.baseUrl = baseUrlValue + "/";
            }
        }

        // Setup the HttpClient to handle connections
        this.httpClient = new HttpClient();
        // Proxy configuration
        String proxyHost = System.getProperty("http.proxyHost);");
        String proxyPortString = System.getProperty("http.proxyPort");
        int proxyPort = -1;

        if (this.getBaseUrl().startsWith("https")) {
            proxyHost = System.getProperty("https.proxyHost");
            proxyPortString = System.getProperty("https.proxyPort");
        }
        try {
            proxyPort = Integer.parseInt(proxyPortString);
        } catch (NumberFormatException nfe) {
            proxyPort = -1;
        }
        if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
            this.httpClient.getHostConfiguration().setProxy(proxyHost, proxyPort);
        }
    }

    /**
     * Gets the username.
     * 
     * @return the username
     */
    public final String getUsername() {
        return this.username;
    }

    /**
     * Gets the password.
     * 
     * @return the password
     */
    public final String getPassword() {
        return this.password;
    }

    /**
     * Gets the base url.
     * 
     * @return the base url
     */
    public final String getBaseUrl() {
        return this.baseUrl;
    }

    /**
     * Gets the export base.
     * 
     * @return the export base
     */
    public final String getExportBase() {
        return this.exportBase;
    }

    /**
     * Gets the xslt filename.
     * 
     * @return the xslt file name
     */
    public final String getXsltFileName() {
        return this.xsltFileName;
    }

    /**
     * Gets the revert flag.
     * 
     * @return the revert flag
     */
    public final boolean getRevert() {
        return this.revert;
    }

    /**
     * Gets the Jira security key.
     * 
     * @return the jira security key
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public final String getKey() throws IOException {

        String key = "";

        final String jellyUrl = this.getBaseUrl() + this.jiraKeyPath;

        final PostMethod postMethod = new PostMethod(jellyUrl);
        final NameValuePair[] data = {
                new NameValuePair("os_username", this.getUsername()),
                new NameValuePair("os_password", this.getPassword())
                };       

        final String raw = postData(postMethod, data);

        // Get the value of the alt_token input field as the key
        if (raw.indexOf("name=\"atl_token\"") > 0) {
            final int charcount = 16;
            final int valuelength = 20;
            // Value returned
            String result = raw.substring(raw.indexOf("name=\"atl_token\"") + charcount,
                    raw.length());
            result = result.substring(result.indexOf("\""), result.indexOf("\"")
                    + valuelength);
            key = result.substring(result.indexOf("\"") + 1, result.lastIndexOf("\""));
        }
        return key;
    }
    
    
    /**
     * Passes the XML to Jira's Jelly runner.
     *
     * @param key the key
     * @param xmldata the xmldata
     * @return the result from the jelly runner
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public final String importXML(final String key, final String xmldata)
            throws IOException {

        String result = "";
                    
        final String runnerUrl = this.getBaseUrl() + this.jiraRunnerPath;

        PostMethod postMethod = new PostMethod(runnerUrl);
        NameValuePair[] data = {
                new NameValuePair("os_username", this.getUsername()),
                new NameValuePair("os_password", this.getPassword()),
                new NameValuePair("atl_token", key),
                new NameValuePair("file", ""),
                new NameValuePair("script", xmldata)
                };
        
        final String raw = postData(postMethod, data);
                
        if (raw.indexOf("xmlns:j='jelly:core'") > 0 || 
                raw.indexOf("xmlns:j=\"jelly:core\"") > 0) {
            // The Jelly script has successfully run, capture outcome
            final int precount = 24;
            final int postcount = 4;
            // Value returned
            String startResult = raw.substring(raw.indexOf("xmlns:j='jelly:core'") 
                    + precount, raw.length());
            result = startResult.substring(0, startResult.indexOf("/JiraJelly") 
                    - postcount);
        }
        
        if (raw.indexOf("xmlns:j=&quot;jelly:core&quot;") > 0) {
            // The Jelly script has been run, capture outcome
            final int precount = 34;
            final int postcount = 4;
            // Value returned
            String startResult = raw.substring(
                    raw.indexOf("xmlns:j=&quot;jelly:core&quot;") + precount, 
                    raw.length());
            result = startResult.substring(0, startResult.indexOf("/JiraJelly") 
                    - postcount);
        }
        
        if (raw.indexOf("id=\"scriptException\"") > 0) {
            // A script exception has been thrown. i.e. The script couldn't be run
            final int precount = 203;
            final int postcount = 1;
            // Value returned
            String startResult = raw.substring(raw.indexOf("id=\"scriptException\"") 
                    + precount, raw.length());
            result = startResult.substring(0, startResult.indexOf("/div") 
                    - postcount);
        }
        
        return reformatHtml(result);
    }

    
    /**
     * Parses the xml index.
     *
     * @return the list
     * @throws SAXException the SAX exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public final List<JBTIssue> parseXmlIndex() throws SAXException, IOException {

        final List<JBTIssue> issues = new ArrayList<JBTIssue>();
        
        final File file = new File(this.getExportBase() + "index.xml");
        
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException pce) {
            throw new SAXException("Error configuring XML parser: " + pce.getMessage());
        }
        final Document doc = db.parse(file);
        doc.getDocumentElement().normalize();
        
        final NodeList bugList = doc.getElementsByTagName("bug");

        for (int s = 0; s < bugList.getLength(); s++) {

            final Node bugNode = bugList.item(s);

            if (bugNode.getNodeType() == Node.ELEMENT_NODE) {

                final JBTIssue issue = new JBTIssue(this);
                
                final Element bugElmnt = (Element) bugNode;
                
                issue.setId(bugElmnt.getAttribute("id"));
                issue.setBase(bugElmnt.getAttribute("base"));
                                
                NodeList fileElmntLst = bugNode.getChildNodes();
                
                for (int t = 0; t < fileElmntLst.getLength(); t++) {

                    final Node fileNode = fileElmntLst.item(t);
                    
                    if (fileNode.getNodeType() == Node.ELEMENT_NODE) {
                    
                        final Element fileElmnt = (Element) fileNode;
                        final NodeList chldFile = fileElmnt.getChildNodes();
                        final Node filenameNode = (Node) chldFile.item(0);
                    
                        if (StringUtils.equals(
                                fileElmnt.getAttribute("primary"), "true")) {
                            issue.setFileName(filenameNode.getNodeValue());
                            
                            // Add the issue to the array
                            issues.add(issue);
                        }
                    }
                }
            }
        }
        return issues;
    }

    
    /**
     * Load xml data from the supplied file.
     * 
     * @param filepath the filepath
     * @return the string
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public final String loadXmlDataFile(final String filepath) throws IOException {

        StringBuffer contents = new StringBuffer();

        Reader reader = new InputStreamReader(new FileInputStream(filepath), "UTF-8");

        int ch;
        do {
            ch = reader.read();
            if (ch != -1) {
                contents.append((char) ch);
            }
        } while (ch != -1);

        reader.close();

        return contents.toString();
    }
    
    
    /**
     * Post the data to the service.
     *
     * @param postMethod the post method
     * @param data the data
     * @return the string
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private String postData(final PostMethod postMethod, final NameValuePair[] data)
            throws IOException {

        final StringBuffer raw = new StringBuffer();
        
        postMethod.setRequestBody(data);

        // Execute the post request
        httpClient.executeMethod(postMethod);

        try {
            Reader reader = new InputStreamReader(postMethod.getResponseBodyAsStream(),
                    postMethod.getResponseCharSet());
            // consume the response entity
            int dataread = reader.read();
            while (dataread != -1) {
                char theChar = (char) dataread;
                raw.append(theChar);
                dataread = reader.read();
            }
        } finally {
            postMethod.releaseConnection();
        }
        return raw.toString();
    }

    
    /**
     * Reformat html.
     *
     * @param input the input
     * @return the string
     */
    private String reformatHtml(final String input) {

        String result = StringUtils.replace(input, "<BR>", "\n");
        result = StringUtils.replace(result, "<b>", "");
        result = StringUtils.replace(result, "</b>", "");
        result = StringUtils.replace(result, "&nbsp;", " ");
        result = StringUtils.replace(result, "&gt;", ">");
        result = StringUtils.replace(result, "&lt;", "<");
        result = StringUtils.replace(result, "&quot;", "\"");
        result = StringUtils.replace(result, ".<br/>", "");
        result = StringUtils.replace(result, ".<BR/>", "");
        result = StringUtils.replace(result, ".<br>", "");
        result = StringUtils.replace(result, ".<BR>", "");
        
        return result;
    }
}
