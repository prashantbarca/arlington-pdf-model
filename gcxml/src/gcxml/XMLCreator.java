/*
* XMLCreator.java
* Copyright 2020 PDF Association, Inc. https://www.pdfa.org
*
* This material is based upon work supported by the Defense Advanced
* Research Projects Agency (DARPA) under Contract No. HR001119C0079.
* Any opinions, findings and conclusions or recommendations expressed
* in this material are those of the author(s) and do not necessarily
* reflect the views of the Defense Advanced Research Projects Agency
* (DARPA). Approved for public release.
*
* SPDX-License-Identifier: Apache-2.0
* Contributors: Roman Toda, Frantisek Forgac, Normex
*/
package gcxml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author fero
 */
public class XMLCreator {
    private String input_folder;
    private String output_folder;
    private TSVHandler tsv = null;
    private double pdf_ver = 0; 
    private File[] list_of_files = null;
    private char delimiter = '\t';
    
    private String current_entry;
    
    private DocumentBuilderFactory dom_factory = null;
    private DocumentBuilder dom_builder = null;
    private Document new_doc = null;
    
    /**
     * Converts the Arlington PDF Model from a TSV file set to a single 
     * monolithic XML representation.
     * 
     * @param list_of_files  array of Arlington TSV file names
     * @param delimiter    should be '\t'
     */
    public XMLCreator(File[] list_of_files, char delimiter) {
        this.output_folder = System.getProperty("user.dir") + "/xml/";
        this.input_folder  = System.getProperty("user.dir") + "/tsv/latest/";
        
        // sort Arlington TSV files by name alphabetically
        ArrayList<File> arr_file = new ArrayList<>();
        for (File file : list_of_files) {
            arr_file.add(file);
        }
        arr_file.sort((p1, p2) -> p1.compareTo(p2));
        
        this.list_of_files = new File[arr_file.size()];
        for (int i = 0; i < arr_file.size(); i++) {
            this.list_of_files[i] = arr_file.get(i);
        }
        
        this.delimiter = delimiter;
        this.current_entry = "";
        
        try {
            dom_factory = DocumentBuilderFactory.newInstance();
            dom_builder = dom_factory.newDocumentBuilder();
            new_doc = dom_builder.newDocument();
        } 
        catch (Exception exp) {
            System.err.println("ERROR: " + exp.toString());
        }
    }
    
    /**
     * Creates a specific XML file for a specific PDF version based on 
     * an Arlington TSV file set.
     * 
     * @param pdf_version  the PDF version (as a string)
     */
    public void createXML(String pdf_version) {
        output_folder += "pdf_grammar" + pdf_version + ".xml" ;
        tsv = new TSVHandler();
        pdf_ver = Float.parseFloat(pdf_version);
        
        int object_count = 0;
        try {
            // Root element
            Element root_elem = new_doc.createElement("PDF");
            root_elem.setAttribute("pdf_version", pdf_version);
            root_elem.setAttribute("grammar_version", Gcxml.Gcxml_version);
            root_elem.setAttribute("iso_ref", "ISO 32000-2:2020");
            new_doc.appendChild(root_elem);
            
            // Read each Arlington TSV file
            for (File file : list_of_files) {
                if (file.isFile() && file.canRead() && file.exists()) {
                    BufferedReader tsv_reader;
                    String temp = input_folder;
                    String file_name = file.getName().substring(0, file.getName().length()-4);
                    temp += file_name + ".tsv";
                    tsv_reader = new BufferedReader(new FileReader(temp));
                    System.out.println("Processing " + file_name + " for PDF " + pdf_version);
                    
                    // remove TSV header row
                    String current_line = tsv_reader.readLine();
                    
                    Element object_elem = new_doc.createElement("OBJECT");
                    object_elem.setAttribute("id", file_name);
                    object_elem.setAttribute("object_number", String.format("%03d",object_count));
                    
                    while ((current_line = tsv_reader.readLine()) != null) {
                        String[] column_values = current_line.split(Character.toString(delimiter), -1);
                        assert (column_values.length == 12) : "Less than 12 TSV columns!";

                        // set instance varaibles for reporting purposes
                        current_entry = column_values[0];
                        float current_entry_version = Float.parseFloat(column_values[2]);

                        // <ENTRY> node: represents single key/array element in the object
                        Element entry_elem = new_doc.createElement("ENTRY");
                        if (current_entry_version <= pdf_ver) {
                            System.out.println("\tKept key: " + current_entry);

                            TSVHandler.TypeListModifier types_reduced = tsv.reduceTypesForVersion(column_values[1], pdf_ver);
                            if (types_reduced.somethingReduced()) {
                                // At least one type got reduced so need to
                                // reduce various other TSV fields accordingly
                                // BEFORE they themselves are reduced
                                column_values[1]  = types_reduced.getReducedTypes();
                                column_values[5]  = types_reduced.reduceCorresponding(column_values[5]);  // IndirectReference
                                column_values[7]  = types_reduced.reduceCorresponding(column_values[7]);  // DefaultValue
                                column_values[9]  = types_reduced.reduceCorresponding(column_values[9]);  // SpecialCase
                            }
                            column_values[4]  = tsv.reduceRequiredForVersion(column_values[4], pdf_ver); // Required
                            column_values[8]  = tsv.reduceComplexForVersion(column_values[8], pdf_ver); // PossibleValues
                            column_values[10] = tsv.reduceComplexForVersion(column_values[10], pdf_ver); // Links

                            // <NAME> node: name of the key
                            Element name_elem = nodeName(column_values[0]);
                            Element introduced_elem = nodeIntroduced(column_values[2]);
                            Element deprecated_elem = nodeDeprecated(column_values[3]);
                            Element required_elem = nodeRequired(column_values[4]);
                            Element indirect_reference_elem = nodeIndirectReference(column_values[5]);
                            Element inheritable = nodeInheritable(column_values[6]);
                            Element special_case_elem = nodeSpecialCase(column_values[9]);


                            // <VALUE> node: possible values that can be used for the entry
                            // colValues[1]: type
                            // colValues[10]: links
                            // colValues[6], colValues[7], colValues[8]: other values (optional)
                            Element value_elem = nodeValues(column_values[1], column_values[7], column_values[8], column_values[10]);

                            if ((name_elem != null) && 
                                    (value_elem != null) && 
                                    (introduced_elem != null) &&
                                    (deprecated_elem != null) && 
                                    (required_elem != null) && 
                                    (indirect_reference_elem != null) &&
                                    (special_case_elem != null)) {
                                //append elements to entry
                                entry_elem.appendChild(name_elem);
                                entry_elem.appendChild(value_elem);
                                entry_elem.appendChild(required_elem);
                                entry_elem.appendChild(indirect_reference_elem);
                                entry_elem.appendChild(inheritable);
                                entry_elem.appendChild(introduced_elem);
                                entry_elem.appendChild(deprecated_elem);
                                entry_elem.appendChild(special_case_elem);
                                // append elements to object
                                object_elem.appendChild(entry_elem);
                            }
                        } 
                        else {
                            System.out.println("\tDropped key: " + current_entry);
                        }
                    } // while row in TSV
                    
                    // append object to root - if there was anyting
                    if (object_elem.hasChildNodes()) {
                        System.out.println("\tAdded to XML for PDF " + pdf_version);
                        object_count++;
                        root_elem.appendChild(object_elem);
                    }
                    tsv_reader.close();                   
                }
            }
            
            // Save the document to the disk file
            // properties setup
            TransformerFactory tran_factory = TransformerFactory.newInstance();
            Transformer transformer = tran_factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
            Source src = new DOMSource(new_doc);
            Result result = new StreamResult(new File(output_folder));
            transformer.transform(src, result);
            System.out.println("Wrote XML for PDF " + pdf_version + " with " + object_count + " objects to " + output_folder);
        } 
        catch (IOException exp) {
            System.err.println(exp.toString());
        } 
        catch (Exception exp) {
            System.err.println(exp.toString());
        }
    }
    
    /**
     * Creates an XML "NAME" element representing the key name or array index.
     * 
     * @param col_value  the key name or array index from TSV "Key" field 
     * (column 1, never blank)
     * 
     * @return a valid Element (always)
     */
    private Element nodeName(String col_value) {
        Element temp_elem = new_doc.createElement("NAME");
        temp_elem.appendChild(new_doc.createTextNode(col_value));
        return temp_elem;
    }
    
    /**
     * Creates an XML "INTRODUCED" element representing the PDF version when
     * the current key/array element was introduced.
     * 
     * @param col_value  the TSV "SinceVersion" field (column 3, never blank)
     * 
     * @return a valid Element (always)
     */
    private Element nodeIntroduced(String col_value) {
        Element temp_elem = new_doc.createElement("INTRODUCED");
        temp_elem.appendChild(new_doc.createTextNode(col_value));
        return temp_elem;
    }
    
    /**
     * Creates an XML "DEPRECATED" element representing the PDF version when
     * the current key/array element was deprecated.
     * 
     * @param col_value  the TSV "DeprecatedIn" field (column 4). Can be empty.
     * 
     * @return a valid Element (always)
     */
    private Element nodeDeprecated(String col_value) {
        Element temp_elem = new_doc.createElement("DEPRECATED");
        temp_elem.appendChild(new_doc.createTextNode(col_value));
        return temp_elem;
    }
    
    
    /**
     * Creates an XML "REQUIRED" element representing the required/optional-ness
     * of the current key/array index. Converted to XML "true"/"false" with
     * predicates remaining unchanged.
     * 
     * @param col_value  the TSV "Required" field which can be TRUE, FALSE or a 
     * predicate. Column 5.
     * 
     * @return a valid Element (always)
     */
    private Element nodeRequired(String col_value) {
        Element temp_elem = new_doc.createElement("REQUIRED");
        if (!col_value.startsWith("fn:")) {
            col_value = col_value.toLowerCase();
        }
        temp_elem.appendChild(new_doc.createTextNode(col_value));
        return temp_elem;
    }
    
    /**
     * Creates an XML "INDIRECT_REFERENCE" element representing whether the 
     * current key/array index is required to be direct, indirect or either.
     * 
     * @param col_value  the TSV "IndirectReference" field which can be complex,
     *  FALSE, TRUE or a predicate. Column 6.
     * 
     * @return a valid Element (always)
     */
    private Element nodeIndirectReference(String col_value) {
        Element temp_elem = new_doc.createElement("INDIRECT_REFERENCE");
        if (col_value.startsWith("fn:")) {
            temp_elem.appendChild(new_doc.createTextNode(col_value));
        }
        else if ((col_value.equals("TRUE")) || (col_value.equals("FALSE"))) {
            temp_elem.appendChild(new_doc.createTextNode(col_value.toLowerCase()));
        }
        else {
            String ir_arr[] = col_value.split(";");
            for (String ir : ir_arr) {
                ir = ir.substring(1, ir.length()-1); // strip [ and ]
                temp_elem.appendChild(new_doc.createTextNode(col_value.toLowerCase()));
            }
        }
        return temp_elem;
    }
    
    
    /**
     * Creates an XML "INHERITABLE" element .
     * 
     * @param col_value  the TSV "Inheritable" field which can only be TRUE or
     * FALSE. Column 7. Converted to XML "true"/"false".
     * 
     * @return null on error, or a valid Element
     */
    private Element nodeInheritable(String col_value) {
        Element temp_elem = new_doc.createElement("INHERITABLE");
        temp_elem.appendChild(new_doc.createTextNode(col_value.toLowerCase()));
        return temp_elem;
    }
    
    /**
     * Creates an XML "DEFAULT_VALUE" element .
     * 
     * @param col_value  the TSV "DefaultValue" field. Column 8. Can be almost
     * anything.
     * 
     * @return a valid Element (always)
     */
    private Element nodeDefaultValue(String col_value) {
        Element temp_elem = new_doc.createElement("DEFAULT_VALUE");
        temp_elem.appendChild(new_doc.createTextNode(col_value));
        return temp_elem;
    }
    
    
    /**
     * Creates an XML "SPECIAL_CASE" element .
     * 
     * @param col_value  the TSV "SpecialCase" field which can be anything.
     * Column 10.
     * 
     * @return a valid Element
     */
    private Element nodeSpecialCase(String col_value) {
        Element temp_elem = new_doc.createElement("SPECIAL_CASE");
        if (!col_value.isBlank()) {
            temp_elem.appendChild(new_doc.createTextNode(col_value));
        }
        return temp_elem;
    }


    /**
     * Converts "DefaultValue" and "PossibleValues" into XML for a set of Types
     * and Links.
     * 
     * @param type an Arlington Type field, possibly complex
     * @param default_value  Arlington "DefaultValue" field, possibly complex
     * @param possible_values Arlington "PossibleValues" field, possibly complex
     * @param links Arlington Links corresponding 
     * 
     * @return a valid XML Element
     */
    private Element nodeValues(String type, String default_value, String possible_values, String links) {
        Element values_elem = new_doc.createElement("VALUES");
        
        String[] types = type.split(";");
        String[] arr_links = links.split(";");
        String[] pos_values = null;
        if (!possible_values.isBlank()) {
            pos_values = possible_values.split(";");
        }
        String[] dft_values = null;
        if (!default_value.isBlank()) {
            dft_values = default_value.split(";");
        }
                
        assert (types.length == arr_links.length) : "Types and Links are not the same length!";
        assert (!type.contains("fn:")) : "Types contained a predicate!";
        assert ((pos_values != null) && (pos_values.length == arr_links.length)) : "PossibleValues and Links are not the same length!";
        assert ((dft_values != null) && (dft_values.length == arr_links.length)) : "DefaultValue and Links are not the same length!";
        
        for (int i = 0; i < types.length; i++) {
            Element value;
            String t = types[i];
            
            // Is it an Arlington type needing a Link?
            if ("array".equals(t) || 
                "dictionary".equals(t) || 
                "stream".equals(t) || 
                "number-tree".equals(t) || 
                "name-tree".equals(t)) {
                // Strip [ and ]
                assert (arr_links[i].charAt(0) == '[') : "No opening [ on Links";
                assert (arr_links[i].charAt(arr_links[i].length()-1) == ']') : "No closing ] on Links";
                String a = arr_links[i].substring(1, arr_links[i].length() - 1);
                
                // COMMAs are ambiguous: separators or inside predicates?
                while (!a.isBlank()) {
                    if (a.startsWith("fn:")) {
                        // get up to closing bracket )
                        int j = tsv.indexOfOuterCloseBracket(a);
                        assert j != -1: "No ')' for predicate!";

                        // Get encapsulating predicate incl. close bracket
                        String s = a.substring(0, j + 1);
                        value = createNodeValue(t, s);
                        values_elem.appendChild(value);

                        if (j + 2 < a.length()) {
                            a = a.substring(j + 2, a.length());
                        }
                        else {
                            a = "";
                        }

                        // remove COMMA if not at end of string
                        if ((!a.isBlank()) && (a.charAt(0) == ',')) {
                            a = a.substring(1, a.length());
                        }
                    }
                    else {
                        String s = a;
                        if (a.indexOf(',') > 0) {
                            s = a.substring(0, a.indexOf(','));
                            a = a.substring(a.indexOf(',') + 1, a.length());
                        }
                        else {
                            a = "";
                        }
                        value = createNodeValue(t, s);
                        values_elem.appendChild(value);
                    }
                } // while
            }
            else { // A basic Arlington type (i.e. no Links)
                if (pos_values != null) {
                    // Strip [ and ]
                    assert (pos_values[i].charAt(0) == '[') : "No opening [ on PossibleValue";
                    assert (pos_values[i].charAt(pos_values[i].length()-1) == ']') : "No closing ] on PossibleValue";
                    String a = pos_values[i].substring(1, pos_values[i].length() - 1);
                    // COMMAs are ambiguous: separators or inside predicates?
                    
                    while (!a.isBlank()) {
                        if (a.startsWith("fn:")) {
                            // get up to closing bracket )
                            int j = tsv.indexOfOuterCloseBracket(a);
                            assert j != -1: "No ')' for predicate!";

                            // Get encapsulating predicate incl. close bracket
                            String s = a.substring(0, j + 1);
                            value = createNodeValue(t, s);
                            values_elem.appendChild(value);

                            if (j + 2 < a.length()) {
                                a = a.substring(j + 2, a.length());
                            }
                            else {
                                a = "";
                            }

                            // remove COMMA if not at end of string
                            if ((!a.isBlank()) && (a.charAt(0) == ',')) {
                                a = a.substring(1, a.length());
                            }
                        }
                        else {
                            String s = a;
                            if (a.indexOf(',') >= 0) {
                                s = a.substring(0, a.indexOf(','));
                                a = a.substring(a.indexOf(',') + 1, a.length());
                            }
                            else {
                                a = "";
                            }
                            value = createNodeValue(t, s);
                            values_elem.appendChild(value);
                        }
                    } // while
                }
                else {
                    // No PossibleValue - use empty value
                    value = createNodeValue(t, "");
                    values_elem.appendChild(value);
                }
                
                // DefaultValue only makes sense for basic types
                if (dft_values != null) {
                    Element default_elem = new_doc.createElement("DEFAULT_VALUE");
                    if (dft_values[i].charAt(0) == '[') {
                        // strip off [ and ]
                        dft_values[i] = dft_values[i].substring(1, dft_values[i].length() - 1);
                    }
                    // Account for degenerate "[]"    
                    if (dft_values[i].length() > 0) {
                        value = createNodeValue(t, dft_values[i]);
                        values_elem.appendChild(value);
                    }
                }
            }
        } // for-each type
        
        return values_elem;
    }

   /**
     * Creates a single "VALUE" element
     * 
     * @param t      an Arlington type (just one)
     * @param value  the value for the VALUE node, possibly with [ and ]
     * @return       a new XML VALUE element
     */
    private Element createNodeValue(String t, String value) {
        Element valueElem = new_doc.createElement("VALUE");
        value = value.replace("[", "");
        value = value.replace("]", "");
        valueElem.setAttribute("type", t);
        valueElem.appendChild(new_doc.createTextNode(value));
        return valueElem;
    }
}
