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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    private Map<Character, String> specialCharacterMap;

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
        this.specialCharacterMap = this.initialiseSpecialCharacterMap();
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
     * Gets the special character map.
     * 
     * @return the special character map
     */
    public final Map<Character, String> getSpecialCharacterMap() {
        return this.specialCharacterMap;
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
    
    /**
     * Gets the special character map.
     *
     * @return the special character map
     */
    private final Map<Character, String> initialiseSpecialCharacterMap() {
        
        final Map<Character, String> map = new HashMap<Character, String>();

        map.put(new Character('\u0100'),"&#256;");
        map.put(new Character('\u0101'),"&#257;");
        map.put(new Character('\u0102'),"&#258;");
        map.put(new Character('\u0103'),"&#259;");
        map.put(new Character('\u0104'),"&#260;");
        map.put(new Character('\u0105'),"&#261;");
        map.put(new Character('\u0106'),"&#262;");
        map.put(new Character('\u0107'),"&#263;");
        map.put(new Character('\u0108'),"&#264;");
        map.put(new Character('\u0108'),"&#265;");
        map.put(new Character('\u010C'),"&#268;");
        map.put(new Character('\u010D'),"&#269;");
        map.put(new Character('\u010E'),"&#270;");
        map.put(new Character('\u010F'),"&#271;");
        map.put(new Character('\u0110'),"&#272;");
        map.put(new Character('\u0111'),"&#273;");
        map.put(new Character('\u0112'),"&#274;");
        map.put(new Character('\u0113'),"&#275;");
        map.put(new Character('\u0118'),"&#280;");
        map.put(new Character('\u0119'),"&#281;");
        map.put(new Character('\u011A'),"&#282;");
        map.put(new Character('\u011B'),"&#283;");
        map.put(new Character('\u011C'),"&#284;");
        map.put(new Character('\u011D'),"&#285;");
        map.put(new Character('\u011E'),"&#286;");
        map.put(new Character('\u011F'),"&#287;");
        map.put(new Character('\u0122'),"&#290;");
        map.put(new Character('\u0123'),"&#291;");
        map.put(new Character('\u0124'),"&#292;");
        map.put(new Character('\u0125'),"&#293;");
        map.put(new Character('\u012A'),"&#298;");
        map.put(new Character('\u012B'),"&#299;");
        map.put(new Character('\u0130'),"&#304;");
        map.put(new Character('\u0131'),"&#305;");
        map.put(new Character('\u0134'),"&#308;");
        map.put(new Character('\u0135'),"&#309;");
        map.put(new Character('\u0136'),"&#310;");
        map.put(new Character('\u0137'),"&#311;");
        map.put(new Character('\u0139'),"&#313;");
        map.put(new Character('\u013A'),"&#314;");
        map.put(new Character('\u013B'),"&#315;");
        map.put(new Character('\u013C'),"&#316;");
        map.put(new Character('\u013D'),"&#317;");
        map.put(new Character('\u013E'),"&#318;");
        map.put(new Character('\u0141'),"&#321;");
        map.put(new Character('\u0142'),"&#322;");
        map.put(new Character('\u0143'),"&#323;");
        map.put(new Character('\u0144'),"&#324;");
        map.put(new Character('\u0145'),"&#325;");
        map.put(new Character('\u0146'),"&#326;");
        map.put(new Character('\u0147'),"&#327;");
        map.put(new Character('\u0148'),"&#328;");
        map.put(new Character('\u0150'),"&#336;");
        map.put(new Character('\u0151'),"&#337;");
        map.put(new Character('\u0154'),"&#340;");
        map.put(new Character('\u0155'),"&#341;");
        map.put(new Character('\u0156'),"&#342;");
        map.put(new Character('\u0157'),"&#343;");
        map.put(new Character('\u0158'),"&#344;");
        map.put(new Character('\u0159'),"&#345;");
        map.put(new Character('\u015A'),"&#346;");
        map.put(new Character('\u015B'),"&#347;");
        map.put(new Character('\u015C'),"&#348;");
        map.put(new Character('\u015D'),"&#349;");
        map.put(new Character('\u015E'),"&#350;");
        map.put(new Character('\u015F'),"&#351;");
        map.put(new Character('\u0160'),"&#352;");
        map.put(new Character('\u0161'),"&#353;");
        map.put(new Character('\u0162'),"&#354;");
        map.put(new Character('\u0163'),"&#355;");
        map.put(new Character('\u0164'),"&#356;");
        map.put(new Character('\u0165'),"&#357;");
        map.put(new Character('\u016A'),"&#362;");
        map.put(new Character('\u016B'),"&#363;");
        map.put(new Character('\u016C'),"&#364;");
        map.put(new Character('\u016D'),"&#365;");
        map.put(new Character('\u016E'),"&#366;");
        map.put(new Character('\u016F'),"&#367;");
        map.put(new Character('\u0170'),"&#368;");
        map.put(new Character('\u0171'),"&#369;");
        map.put(new Character('\u0178'),"&#376;");
        map.put(new Character('\u0179'),"&#377;");
        map.put(new Character('\u017A'),"&#378;");
        map.put(new Character('\u017B'),"&#379;");
        map.put(new Character('\u017C'),"&#380;");
        map.put(new Character('\u017D'),"&#381;");
        map.put(new Character('\u017E'),"&#382;");
        map.put(new Character('\u2116'),"&#8470;");
        map.put(new Character('\u00C1'),"&Aacute;");
        map.put(new Character('\u00E1'),"&aacute;");
        map.put(new Character('\u00C2'),"&Acirc;");
        map.put(new Character('\u00E2'),"&acirc;");
        map.put(new Character('\u00C6'),"&AElig;");
        map.put(new Character('\u00E6'),"&aelig;");
        map.put(new Character('\u00E0'),"&agrave;");
        map.put(new Character('\u00C0'),"&Agrave;");
        map.put(new Character('\u00C5'),"&Aring;");
        map.put(new Character('\u00E5'),"&aring;");
        map.put(new Character('\u00C3'),"&Atilde;");
        map.put(new Character('\u00E3'),"&atilde;");
        map.put(new Character('\u00C4'),"&Auml;");
        map.put(new Character('\u00E4'),"&auml;");
        map.put(new Character('\u2022'),"&bull;");
        map.put(new Character('\u00C7'),"&Ccedil;");
        map.put(new Character('\u00E7'),"&ccedil;");
        map.put(new Character('\u00A9'),"&copy;");
        map.put(new Character('\u2020'),"&dagger;");
        map.put(new Character('\u00B0'),"&deg;");
        map.put(new Character('\u00C9'),"&Eacute;");
        map.put(new Character('\u00E9'),"&eacute;");
        map.put(new Character('\u00CA'),"&Ecirc;");
        map.put(new Character('\u00EA'),"&ecirc;");
        map.put(new Character('\u00C8'),"&Egrave;");
        map.put(new Character('\u00E8'),"&egrave;");
        map.put(new Character('\u00D0'),"&ETH;");
        map.put(new Character('\u00F0'),"&eth;");
        map.put(new Character('\u00CB'),"&Euml;");
        map.put(new Character('\u00EB'),"&euml;");
        map.put(new Character('\u20AC'),"&euro;");
        map.put(new Character('\u00CD'),"&Iacute;");
        map.put(new Character('\u00ED'),"&iacute;");
        map.put(new Character('\u00CE'),"&Icirc;");
        map.put(new Character('\u00EE'),"&icirc;");
        map.put(new Character('\u00A1'),"&iexcl;");
        map.put(new Character('\u00CC'),"&Igrave;");
        map.put(new Character('\u00EC'),"&igrave;");
        map.put(new Character('\u00BF'),"&iquest;");
        map.put(new Character('\u00CF'),"&Iuml;");
        map.put(new Character('\u00EF'),"&iuml;");
        map.put(new Character('\u00AB'),"&laquo;");
        map.put(new Character('\u2014'),"&mdash;");
        map.put(new Character('\u00B5'),"&micro;");
        map.put(new Character('\u00B7'),"&middot;");
        map.put(new Character('\u2013'),"&ndash;");
        map.put(new Character('\u00D1'),"&Ntilde;");
        map.put(new Character('\u00F1'),"&ntilde;");
        map.put(new Character('\u00D3'),"&Oacute;");
        map.put(new Character('\u00F3'),"&oacute;");
        map.put(new Character('\u00D4'),"&Ocirc;");
        map.put(new Character('\u00F4'),"&ocirc;");
        map.put(new Character('\u0152'),"&OElig;");
        map.put(new Character('\u0153'),"&oelig;");
        map.put(new Character('\u00D2'),"&Ograve;");
        map.put(new Character('\u00F2'),"&ograve;");
        map.put(new Character('\u00AA'),"&ordf;");
        map.put(new Character('\u00BA'),"&ordm;");
        map.put(new Character('\u00D8'),"&Oslash;");
        map.put(new Character('\u00F8'),"&oslash;");
        map.put(new Character('\u00D5'),"&Otilde;");
        map.put(new Character('\u00F5'),"&otilde;");
        map.put(new Character('\u00D6'),"&Ouml;");
        map.put(new Character('\u00F6'),"&ouml;");
        map.put(new Character('\u00A3'),"&pound;");
        map.put(new Character('\u00BB'),"&raquo;");
        map.put(new Character('\u00AE'),"&reg;");
        map.put(new Character('\u00DF'),"&szlig;");
        map.put(new Character('\u00DE'),"&THORN;");
        map.put(new Character('\u00FE'),"&thorn;");
        map.put(new Character('\u00DA'),"&Uacute;");
        map.put(new Character('\u00FA'),"&uacute;");
        map.put(new Character('\u00DB'),"&Ucirc;");
        map.put(new Character('\u00FB'),"&ucirc;");
        map.put(new Character('\u00D9'),"&Ugrave;");
        map.put(new Character('\u00F9'),"&ugrave;");
        map.put(new Character('\u00F6'),"&uml;");
        map.put(new Character('\u00DC'),"&Uuml;");
        map.put(new Character('\u00FC'),"&uuml;");
        map.put(new Character('\u00DD'),"&Yacute;");
        map.put(new Character('\u00FD'),"&yacute;");
        map.put(new Character('\u00FF'),"&yuml;");
        
        return map;
    }
}
