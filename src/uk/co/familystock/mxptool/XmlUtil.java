package uk.co.familystock.mxptool;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/**
 * Provides helper methods for working with XML documents.
 *
 * @author bstock@google.com (Benjamin Stock)
 */
final class XmlUtil {

  private XmlUtil() {
    // Utility class, so remove ability to instantiate.
  }
  
  /**
   * Creates and returns a non-validating {@link DocumentBuilder} using the 
   * internally recommended approach.
   */
  static DocumentBuilder getDocumentBuilder() {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(false);
      return factory.newDocumentBuilder();
      //TODO(bstock): Investigate further.
//      SecureJDKXercesDOMFactory parserFactory = SecureXMLParsing.getDOMParserFactory();
//      parserFactory.setValidating(false);
//      return parserFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException("Configuration error while trying to load XML parser", e);
    }
  }
  
  /**
   * Attempts to parse and validate the given XML file after registering the 
   * given handler as the content handler. {@code schemaPath} must be a path to a
   * W3C Schema resource.
   */
  static void parseAndValidateDocument(File xmlFile, String schemaPath, DefaultHandler docHandler)
      throws IOException, SAXException {
    SAXParser parser = null;
    try {
      SAXParserFactory parserFactory = SAXParserFactory.newInstance();
      parserFactory.setValidating(false);
      
      //TODO(bstock): Investigate further.
//      SecureJDKXercesSAXFactory parserFactory = SecureXMLParsing.getSAXParserFactory();
//      parserFactory.setValidating(false); // do not do DTD validation.
      
      SchemaFactory fac = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      StreamSource schemaFile = new StreamSource(XmlUtil.class.getResourceAsStream(schemaPath));
      Schema sc = fac.newSchema(schemaFile);
      parserFactory.setSchema(sc);
      
      parser = parserFactory.newSAXParser();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException("Configuration error while trying to load XML parser", e);
    } catch (SAXException e) {
      throw new RuntimeException("SAX error while trying to load XML parser", e);
    }
    parser.parse(xmlFile, docHandler);
  }

  /**
   * Serialises and prints the contents of the DOM represented by {@code doc}
   * to the given output.
   */
  static void printDom(Document doc, Writer out) {
    try {
      Transformer serializer = TransformerFactory.newInstance().newTransformer();
      serializer.setOutputProperty(OutputKeys.INDENT, "yes");
      serializer.transform(new DOMSource(doc), new StreamResult(out));
    } catch (TransformerException e) {
      throw new RuntimeException("Fatal error trying to print DOM", e);
    }
  }  
}
